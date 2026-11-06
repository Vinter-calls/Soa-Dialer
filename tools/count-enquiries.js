const dns = require('dns');
dns.setServers(['8.8.8.8', '1.1.1.1']);
const { MongoClient } = require('./node_modules/mongodb');

const MONGO_URI = "mongodb+srv://sophiseTech:Jazzir%409496@cluster0.fvcjysb.mongodb.net/aethens?retryWrites=true&w=majority";
const DB_NAME = "aethens";

async function main() {
  const client = new MongoClient(MONGO_URI);
  try {
    await client.connect();
    const db = client.db(DB_NAME);
    const count = await db.collection('enquiries').countDocuments();
    console.log("Total enquiries in aethens.enquiries:", count);

    const samples = await db.collection('enquiries').find({}).limit(5).project({ name: 1, phoneNumber: 1, enquiryNumber: 1 }).toArray();
    console.log("Samples:", samples);
  } catch (err) {
    console.error("Error:", err);
  } finally {
    await client.close();
  }
}

main();
