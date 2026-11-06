const dns = require('dns');
dns.setServers(['8.8.8.8', '1.1.1.1']);
const { MongoClient } = require('mongodb');

const MONGO_URI = "mongodb+srv://sophiseTech:Jazzir%409496@cluster0.fvcjysb.mongodb.net/aethens?retryWrites=true&w=majority";
const DB_NAME = "aethens";

async function main() {
  const client = new MongoClient(MONGO_URI);
  try {
    await client.connect();
    console.log("Connected to MongoDB successfully!");
    const db = client.db(DB_NAME);
    const collections = await db.listCollections().toArray();
    console.log("Collections:", collections.map(c => c.name));

    // Check users collection if exists
    for (const name of ['users', 'staff', 'staffs', 'centers', 'roles', 'managers', 'accounts']) {
      if (collections.some(c => c.name === name)) {
        console.log(`\n--- First 5 docs in '${name}': ---`);
        const docs = await db.collection(name).find({}).limit(5).toArray();
        console.log(JSON.stringify(docs, null, 2));
      }
    }
  } catch (err) {
    console.error("Error connecting to MongoDB:", err);
  } finally {
    await client.close();
  }
}

main();
