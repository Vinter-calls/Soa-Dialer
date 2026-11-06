const dns = require('dns');
dns.setServers(['8.8.8.8', '1.1.1.1']);

const { MongoClient, ObjectId } = require('mongodb');
const sqlite3 = require('sqlite3').verbose();
const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const readline = require('readline');

// CONFIGURATION
const MONGO_URI = "mongodb+srv://sophiseTech:Jazzir%409496@cluster0.fvcjysb.mongodb.net/aethens?retryWrites=true&w=majority";
const DB_NAME = "aethens";
const DEFAULT_CENTER_ID = "67cd080a040cb823ea6c0744";
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
    return execSync(cmd, { encoding: 'utf8', stdio: ['pipe', 'pipe', 'ignore'] }).trim();
  } catch (err) {
    return null;
  }
}

function prompt(query) {
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
  });
  return new Promise(resolve => rl.question(query, ans => {
    rl.close();
    resolve(ans.trim());
  }));
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

function mapStatusToStage(status) {
  if (!status) return "New";
  const s = status.toLowerCase();
  if (s.includes("enrolled") || s.includes("converted")) return "Enrolled";
  if (s.includes("follow")) return "Follow-up";
  if (s.includes("lost")) return "Lost";
  if (s.includes("spam")) return "Spam";
  return "New";
}

async function getConnectedDevice() {
  let output = runCmd(`${ADB} devices`);
  let lines = (output || "").split(/\r?\n/).slice(1).map(l => l.trim()).filter(Boolean);
  let devices = lines.filter(l => l.endsWith("\tdevice") || l.endsWith(" device")).map(l => l.split(/\s+/)[0]);

  if (devices.length > 0) return devices[0];

  console.log("\n[!] No active ADB device detected.");
  console.log("    If connected via Wi-Fi, enter your phone's IP:PORT from Developer Options");
  console.log("    (e.g., 192.168.1.4:44379), or press Enter if connected via USB cable.");
  const answer = await prompt("\nEnter IP:PORT (or leave blank to retry USB): ");

  if (answer) {
    console.log(`Connecting to ${answer}...`);
    runCmd(`${ADB} connect ${answer}`);
    output = runCmd(`${ADB} devices`);
    lines = (output || "").split(/\r?\n/).slice(1).map(l => l.trim()).filter(Boolean);
    devices = lines.filter(l => l.endsWith("\tdevice") || l.endsWith(" device")).map(l => l.split(/\s+/)[0]);
    if (devices.length > 0) return devices[0];
  }

  // Retry once
  output = runCmd(`${ADB} devices`);
  lines = (output || "").split(/\r?\n/).slice(1).map(l => l.trim()).filter(Boolean);
  devices = lines.filter(l => l.endsWith("\tdevice") || l.endsWith(" device")).map(l => l.split(/\s+/)[0]);
  return devices.length > 0 ? devices[0] : null;
}

async function main() {
  console.log("=================================================");
  console.log("      SOA PHONE <-> MONGODB ATLAS SYNC TOOL      ");
  console.log("=================================================\n");

  // Step 1: Detect ADB device
  console.log("[1/5] Checking phone connection...");
  const deviceId = await getConnectedDevice();
  if (!deviceId) {
    console.error("\n[ERROR] Could not connect to your phone via ADB.");
    console.error("Please ensure Wireless Debugging or USB Debugging is enabled on your phone.");
    process.exit(1);
  }
  console.log(`[+] Connected to device: ${deviceId}`);

  // Step 2: Extract SQLite database from phone
  console.log("\n[2/5] Pulling local enquiries database from phone...");
  const tempDbLocalPath = path.join(__dirname, "temp_phone_db.db");
  const tempRemotePath = "/sdcard/Download/soa_sync_tmp.db";

  // Try current package first, fallback to previous if needed
  let pkg = PACKAGE_NAME;
  let copied = runCmd(`${ADB} -s ${deviceId} shell "run-as ${pkg} cp databases/enquiry_dialer.db ${tempRemotePath}"`);
  if (copied === null) {
    pkg = FALLBACK_PACKAGE_NAME;
    runCmd(`${ADB} -s ${deviceId} shell "run-as ${pkg} cp databases/enquiry_dialer.db ${tempRemotePath}"`);
  }

  runCmd(`${ADB} -s ${deviceId} pull ${tempRemotePath} "${tempDbLocalPath}"`);
  runCmd(`${ADB} -s ${deviceId} shell "rm -f ${tempRemotePath}"`);

  if (!fs.existsSync(tempDbLocalPath) || fs.statSync(tempDbLocalPath).size === 0) {
    console.error("[ERROR] Failed to pull database from phone. Make sure the app has been opened at least once.");
    process.exit(1);
  }
  console.log(`[+] Successfully retrieved phone database (${fs.statSync(tempDbLocalPath).size} bytes)`);

  // Step 3: Read phone enquiries
  console.log("\n[3/5] Reading enquiries from phone database...");
  const phoneEnquiries = await new Promise((resolve, reject) => {
    const db = new sqlite3.Database(tempDbLocalPath, sqlite3.OPEN_READONLY, err => {
      if (err) return reject(err);
      db.all("SELECT * FROM enquiries", [], (err, rows) => {
        db.close();
        if (err) return reject(err);
        resolve(rows || []);
      });
    });
  });

  console.log(`[+] Found ${phoneEnquiries.length} enquiries on the phone.`);

  // Step 4: Connect to MongoDB Atlas
  console.log("\n[4/5] Connecting to MongoDB Atlas cluster...");
  const client = new MongoClient(MONGO_URI);
  await client.connect();
  const db = client.db(DB_NAME);
  const enquiriesCol = db.collection("enquiries");
  const countersCol = db.collection("enquirycounters");

  // Fetch all existing enquiries from MongoDB Atlas
  const mongoEnquiries = await enquiriesCol.find().toArray();
  console.log(`[+] Fetched ${mongoEnquiries.length} existing enquiries from MongoDB Atlas.`);

  // Create a map of 10-digit normalized phone numbers in MongoDB
  const mongoPhoneMap = new Map();
  for (const me of mongoEnquiries) {
    const key = normalize10(me.phoneNumber);
    if (key && !mongoPhoneMap.has(key)) {
      mongoPhoneMap.set(key, me);
    }
  }

  // Get current sequence number
  let counterDoc = await countersCol.findOne({ _id: "enquiryNumber" });
  let nextSeq = counterDoc ? counterDoc.seq : (mongoEnquiries.reduce((max, e) => Math.max(max, e.enquiryNumber || 0), 120) + 1);

  // Step 5: Perform Synchronization
  console.log("\n[5/5] Synchronizing phone leads to MongoDB Atlas...");
  let insertedCount = 0;
  let updatedCount = 0;
  let skippedCount = 0;

  for (const pe of phoneEnquiries) {
    const key = normalize10(pe.phone_number);
    if (!key) continue;

    if (!mongoPhoneMap.has(key)) {
      // New lead -> Insert into MongoDB
      const newDoc = {
        enquiryNumber: nextSeq++,
        name: pe.name || "Customer",
        phoneNumber: formatE164(pe.phone_number),
        foundUsBy: "Direct / Phone",
        modeOfEnquiry: "Call",
        ageCategory: pe.enquiry_type || "General",
        classPreference: "Any",
        selectedCourses: [],
        stage: mapStatusToStage(pe.status),
        demoSlot: {
          status: "Scheduled",
          postponeCount: 0,
          confirmationMessage: null
        },
        centerId: DEFAULT_CENTER_ID,
        isTransferred: false,
        place: pe.company || "Bangalore",
        stageHistory: [],
        followUpHistory: [],
        demoSlotHistory: [],
        centerTransfers: [],
        createdAt: new Date(),
        updatedAt: new Date()
      };

      await enquiriesCol.insertOne(newDoc);
      mongoPhoneMap.set(key, newDoc);
      insertedCount++;
      console.log(`  [+] INSERTED #${newDoc.enquiryNumber}: ${newDoc.name} (${newDoc.phoneNumber}) [${newDoc.stage}]`);
    } else {
      // Existing lead -> Check if status or notes updated on phone
      const existing = mongoPhoneMap.get(key);
      const phoneStage = mapStatusToStage(pe.status);
      if (phoneStage !== existing.stage || (pe.notes && pe.notes !== existing.notes)) {
        await enquiriesCol.updateOne(
          { _id: existing._id },
          {
            $set: {
              stage: phoneStage,
              updatedAt: new Date()
            }
          }
        );
        updatedCount++;
        console.log(`  [^] UPDATED: ${existing.name} (${existing.phoneNumber}) -> Stage: ${phoneStage}`);
      } else {
        skippedCount++;
      }
    }
  }

  // Update counter in MongoDB
  await countersCol.updateOne(
    { _id: "enquiryNumber" },
    { $set: { seq: nextSeq } },
    { upsert: true }
  );

  // Cleanup temp local file
  try {
    if (fs.existsSync(tempDbLocalPath)) fs.unlinkSync(tempDbLocalPath);
  } catch (_) {}

  await client.close();

  console.log("\n=================================================");
  console.log("              SYNC SUMMARY REPORT                ");
  console.log("=================================================");
  console.log(`  Total Phone Enquiries Scanned : ${phoneEnquiries.length}`);
  console.log(`  New Leads Added to MongoDB    : ${insertedCount}`);
  console.log(`  Leads Updated in MongoDB      : ${updatedCount}`);
  console.log(`  Already Up-to-Date in MongoDB : ${skippedCount}`);
  console.log(`  Current Total in MongoDB Atlas: ${mongoEnquiries.length + insertedCount}`);
  console.log("=================================================\n");
  console.log("[SUCCESS] MongoDB Atlas is fully up to date with your phone!\n");
}

main().catch(err => {
  console.error("\n[ERROR] Sync failed:", err);
  process.exit(1);
});
