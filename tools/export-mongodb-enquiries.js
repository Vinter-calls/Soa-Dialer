const dns = require('dns');
dns.setServers(['8.8.8.8', '1.1.1.1']);
const { MongoClient } = require('./node_modules/mongodb');
const fs = require('fs');
const path = require('path');

const MONGO_URI = "mongodb+srv://sophiseTech:Jazzir%409496@cluster0.fvcjysb.mongodb.net/aethens?retryWrites=true&w=majority";
const DB_NAME = "aethens";

async function main() {
  const client = new MongoClient(MONGO_URI);
  try {
    await client.connect();
    const db = client.db(DB_NAME);
    const docs = await db.collection('enquiries').find({}).toArray();
    console.log(`Fetched ${docs.length} enquiries from MongoDB.`);

    const formatted = docs.map((doc, idx) => {
      const enqNum = doc.enquiryNumber !== undefined ? doc.enquiryNumber : (idx + 1);
      return {
        id: doc._id.toString(),
        enquiryNumber: enqNum,
        name: doc.name || "Customer",
        phoneNumber: doc.phoneNumber || "",
        company: doc.place || "School of Athens",
        enquiryType: doc.ageCategory || "General Enquiry",
        status: doc.stage || "New Lead",
        notes: `MongoDB ID: #${enqNum}`,
        createdAt: doc.createdAt ? doc.createdAt.toISOString() : new Date().toISOString()
      };
    });

    const targetPath = path.resolve(__dirname, '../app/src/main/assets/initial_enquiries.json');
    fs.writeFileSync(targetPath, JSON.stringify(formatted, null, 2), 'utf8');
    console.log(`Wrote ${formatted.length} updated enquiries to ${targetPath}`);
  } catch (err) {
    console.error("Error:", err);
  } finally {
    await client.close();
  }
}

main();
