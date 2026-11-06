const dns = require('dns');
dns.setServers(['8.8.8.8', '1.1.1.1']);
const { MongoClient } = require('mongodb');

const MONGO_URI = "mongodb+srv://sophiseTech:Jazzir%409496@cluster0.fvcjysb.mongodb.net/aethens?retryWrites=true&w=majority";
const DB_NAME = "aethens";

async function main() {
  const client = new MongoClient(MONGO_URI);
  try {
    await client.connect();
    const db = client.db(DB_NAME);

    console.log("--- 3 samples from 'enquiries': ---");
    const enqs = await db.collection('enquiries').find({}).limit(3).toArray();
    for (const e of enqs) {
      console.log(JSON.stringify(e, null, 2));
    }

    console.log("--- 2 samples from 'students': ---");
    const students = await db.collection('students').find({}).limit(2).toArray();
    for (const s of students) {
      console.log(JSON.stringify(s, null, 2));
    }
  } catch (err) {
    console.error("Error:", err);
  } finally {
    await client.close();
  }
}

main();
