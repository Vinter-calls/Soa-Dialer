const dns = require('dns');
dns.setServers(['8.8.8.8', '1.1.1.1']);

const { MongoClient, ObjectId } = require('mongodb');
const sqlite3 = require('sqlite3').verbose();
const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

// CONFIGURATION
const MONGO_URI = "mongodb+srv://sophiseTech:Jazzir%409496@cluster0.fvcjysb.mongodb.net/aethens?retryWrites=true&w=majority";
const DB_NAME = "aethens";
const DEFAULT_CENTER_ID = "67cd080a040cb823ea6c0744"; // Whitefield
const PACKAGE_NAME = "com.soa.phone";
const FALLBACK_PACKAGE_NAME = "com.dialer.truedialer";

const ADB_CANDIDATES = [
  "C:\\Users\\joyal\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe",
  "adb"
];

function getAdbPath() {
  for (const p of ADB_CANDIDATES) {
    try {
      if (fs.existsSync(p)) return `"${p}"`;
    } catch (_) {}
  }
  return "adb";
}

const ADB = getAdbPath();

function runCmd(cmd) {
  try {
    return execSync(cmd, { encoding: 'utf8', maxBuffer: 50 * 1024 * 1024 }).trim();
  } catch (err) {
    return null;
  }
}

function normalize10(phone) {
  if (!phone) return "";
  const digits = phone.replace(/\D/g, '');
  return digits.length >= 10 ? digits.slice(-10) : digits;
}

function formatE164(phone) {
  if (!phone) return "";
  const clean = phone.trim();
  if (clean.startsWith('+')) return clean;
  const digits = clean.replace(/\D/g, '');
  if (digits.length === 10) return `+91${digits}`;
  if (digits.length === 12 && digits.startsWith('91')) return `+${digits}`;
  return clean;
}

function mapCallType(typeInt) {
  switch (parseInt(typeInt, 10)) {
    case 1: return "INCOMING";
    case 2: return "OUTGOING";
    case 3: return "MISSED";
    case 4: return "VOICEMAIL";
    case 5: return "REJECTED";
    case 6: return "BLOCKED";
    default: return "UNKNOWN";
  }
}

async function getConnectedDevice() {
  let output = runCmd(`${ADB} devices`);
  let lines = (output || "").split(/\r?\n/).slice(1).map(l => l.trim()).filter(Boolean);
  let devices = lines.filter(l => l.endsWith("\tdevice") || l.endsWith(" device")).map(l => l.split(/\s+/)[0]);
  if (devices.length > 0) return devices[0];

  console.log("Connecting to phone at 192.168.1.4:33119...");
  runCmd(`${ADB} connect 192.168.1.4:33119`);
  output = runCmd(`${ADB} devices`);
  lines = (output || "").split(/\r?\n/).slice(1).map(l => l.trim()).filter(Boolean);
  devices = lines.filter(l => l.endsWith("\tdevice") || l.endsWith(" device")).map(l => l.split(/\s+/)[0]);
  return devices.length > 0 ? devices[0] : null;
}

async function extractAppDatabase(deviceId) {
  const tempDbLocalPath = path.join(__dirname, "temp_calls_phone_db.db");
  const tempRemotePath = "/sdcard/Download/soa_calls_tmp.db";

  let pkg = PACKAGE_NAME;
  let copied = runCmd(`${ADB} -s ${deviceId} shell "run-as ${pkg} cp databases/enquiry_dialer.db ${tempRemotePath}"`);
  if (copied === null) {
    pkg = FALLBACK_PACKAGE_NAME;
    runCmd(`${ADB} -s ${deviceId} shell "run-as ${pkg} cp databases/enquiry_dialer.db ${tempRemotePath}"`);
  }

  runCmd(`${ADB} -s ${deviceId} pull ${tempRemotePath} "${tempDbLocalPath}"`);
  runCmd(`${ADB} -s ${deviceId} shell "rm -f ${tempRemotePath}"`);

  if (!fs.existsSync(tempDbLocalPath)) return null;
  return tempDbLocalPath;
}

async function readAppData(dbPath) {
  return new Promise((resolve) => {
    if (!dbPath || !fs.existsSync(dbPath)) return resolve({ enquiries: [], callStats: [], callNotes: [] });
    const db = new sqlite3.Database(dbPath, sqlite3.OPEN_READONLY, (err) => {
      if (err) return resolve({ enquiries: [], callStats: [], callNotes: [] });
      db.all("SELECT * FROM enquiries", [], (e1, enquiries) => {
        db.all("SELECT * FROM call_stats", [], (e2, callStats) => {
          db.all("SELECT * FROM call_notes", [], (e3, callNotes) => {
            db.close();
            resolve({
              enquiries: enquiries || [],
              callStats: callStats || [],
              callNotes: callNotes || []
            });
          });
        });
      });
    });
  });
}

function parseAndroidCallLogs(rawOutput) {
  const records = [];
  if (!rawOutput) return records;

  const rows = rawOutput.split(/\n(?=Row: \d+ )/);
  for (const row of rows) {
    const fields = {};
    const parts = row.match(/([a-zA-Z0-9_]+)=([^,\n]+(?:,[^,\n]+)*?)(?=, [a-zA-Z0-9_]+=|$)/g);
    if (!parts) continue;

    for (const p of parts) {
      const eqIdx = p.indexOf('=');
      if (eqIdx > 0) {
        const k = p.substring(0, eqIdx).trim();
        const v = p.substring(eqIdx + 1).trim();
        fields[k] = v === "NULL" ? null : v;
      }
    }

    if (fields.number && fields.date) {
      records.push({
        callLogId: fields._id ? fields._id.toString() : null,
        phoneNumber: fields.number,
        normalizedNumber: fields.normalized_number || fields.number,
        contactName: fields.name || fields.preferred_display_name || null,
        callType: mapCallType(fields.type),
        rawType: fields.type,
        durationSeconds: parseInt(fields.duration || "0", 10),
        timestamp: parseInt(fields.date || "0", 10),
        date: new Date(parseInt(fields.date || "0", 10)),
        isRead: fields.is_read === "1",
        countryIso: fields.countryiso || "IN",
        geocodedLocation: fields.geocoded_location || null
      });
    }
  }
  return records;
}

async function main() {
  console.log("=================================================");
  console.log("   SYNC CALL RECORDS TO MONGODB ATLAS (calls)    ");
  console.log("=================================================\n");
  console.log(`Target MongoDB Cluster : cluster0.fvcjysb.mongodb.net`);
  console.log(`Database               : ${DB_NAME}`);
  console.log(`Collection             : calls\n`);

  // 1. Device check
  console.log("[1/5] Connecting to Android phone via ADB...");
  const deviceId = await getConnectedDevice();
  if (!deviceId) {
    console.error("[ERROR] No device connected via ADB. Please check Wi-Fi debugging connection.");
    process.exit(1);
  }
  console.log(`[+] Connected device: ${deviceId}`);

  // 2. Fetch Android System Call Logs
  console.log("\n[2/5] Fetching call history from Android call logs...");
  const rawLogs = runCmd(`${ADB} -s ${deviceId} shell "content query --uri content://call_log/calls"`);
  const callLogs = parseAndroidCallLogs(rawLogs);
  console.log(`[+] Extracted ${callLogs.length} call log entries from device.`);

  // 3. Fetch app SQLite database (Enquiries, Call Stats, Notes)
  console.log("\n[3/5] Pulling Soa Phone app database for caller intelligence...");
  const dbLocalPath = await extractAppDatabase(deviceId);
  const { enquiries, callStats, callNotes } = await readAppData(dbLocalPath);
  console.log(`[+] App database retrieved: ${enquiries.length} enquiries, ${callStats.length} call stats, ${callNotes.length} call notes.`);

  // Build lookup maps for lead & note association
  const leadMap = new Map();
  for (const enq of enquiries) {
    const key = normalize10(enq.phone_number);
    if (key) leadMap.set(key, enq);
  }

  const notesMap = new Map();
  for (const cn of callNotes) {
    const key = normalize10(cn.phone_number);
    if (key) {
      if (!notesMap.has(key)) notesMap.set(key, []);
      notesMap.get(key).push(cn);
    }
  }

  // 4. Connect to MongoDB Atlas
  console.log("\n[4/5] Connecting to MongoDB Atlas cluster0.fvcjysb.mongodb.net...");
  const client = new MongoClient(MONGO_URI);
  await client.connect();
  const db = client.db(DB_NAME);
  const callsCol = db.collection("calls");

  // Create unique index to prevent duplicate call insertions
  try {
    await callsCol.createIndex({ callId: 1 }, { unique: true, sparse: true });
    await callsCol.createIndex({ phoneNumber: 1, timestamp: 1 });
  } catch (_) {}

  // Fetch existing call IDs / timestamps
  const existingCalls = await callsCol.find({}, { projection: { callId: 1, phoneNumber: 1, timestamp: 1 } }).toArray();
  const existingSet = new Set();
  for (const ec of existingCalls) {
    if (ec.callId) existingSet.add(ec.callId);
    if (ec.phoneNumber && ec.timestamp) {
      existingSet.add(`${normalize10(ec.phoneNumber)}_${ec.timestamp}`);
    }
  }
  console.log(`[+] Found ${existingCalls.length} existing records in aethens.calls.`);

  // 5. Sync call records
  console.log("\n[5/5] Processing and syncing calls to MongoDB...");
  const newCallDocs = [];
  let skipped = 0;

  for (const log of callLogs) {
    const normKey = normalize10(log.phoneNumber);
    const uniqueKey = log.callLogId ? `android_${log.callLogId}` : `${normKey}_${log.timestamp}`;
    const altKey = `${normKey}_${log.timestamp}`;

    if (existingSet.has(uniqueKey) || existingSet.has(altKey)) {
      skipped++;
      continue;
    }

    const matchedEnquiry = leadMap.get(normKey);
    const matchedNotes = notesMap.get(normKey) || [];

    const callDoc = {
      callId: uniqueKey,
      phoneNumber: formatE164(log.phoneNumber),
      rawPhoneNumber: log.phoneNumber,
      contactName: log.contactName || (matchedEnquiry ? matchedEnquiry.name : null),
      callType: log.callType,
      durationSeconds: log.durationSeconds,
      timestamp: log.timestamp,
      callDate: log.date,
      centerId: DEFAULT_CENTER_ID,
      centerName: "Whitefield",
      enquiryId: matchedEnquiry ? matchedEnquiry.id : null,
      leadStatus: matchedEnquiry ? matchedEnquiry.status : null,
      leadRequirement: matchedEnquiry ? matchedEnquiry.enquiry_type : null,
      notes: matchedNotes.map(n => n.note_text).join("; "),
      metadata: {
        source: "SoaPhone-Android",
        countryIso: log.countryIso,
        geocodedLocation: log.geocodedLocation,
        isRead: log.isRead
      },
      createdAt: new Date(),
      updatedAt: new Date()
    };

    newCallDocs.push(callDoc);
    existingSet.add(uniqueKey);
    existingSet.add(altKey);
  }

  let insertedCount = 0;
  if (newCallDocs.length > 0) {
    // Insert in batches of 500
    const batchSize = 500;
    for (let i = 0; i < newCallDocs.length; i += batchSize) {
      const batch = newCallDocs.slice(i, i + batchSize);
      const res = await callsCol.insertMany(batch, { ordered: false });
      insertedCount += res.insertedCount;
      console.log(`  [+] Uploaded batch of ${res.insertedCount} calls (${i + res.insertedCount}/${newCallDocs.length})...`);
    }
  }

  // Cleanup local temp db
  if (dbLocalPath && fs.existsSync(dbLocalPath)) {
    try { fs.unlinkSync(dbLocalPath); } catch (_) {}
  }

  const finalTotal = await callsCol.countDocuments();
  await client.close();

  console.log("\n=================================================");
  console.log("            CALL SYNC SUMMARY REPORT             ");
  console.log("=================================================");
  console.log(`  Device Call Logs Scanned  : ${callLogs.length}`);
  console.log(`  New Calls Synced to Atlas : ${insertedCount}`);
  console.log(`  Already Synced (Skipped)  : ${skipped}`);
  console.log(`  Total Calls in aethens    : ${finalTotal}`);
  console.log("=================================================\n");
  console.log("[SUCCESS] All calls are successfully synchronized to MongoDB Atlas!\n");
}

main().catch(err => {
  console.error("\n[ERROR] Call sync failed:", err);
  process.exit(1);
});
