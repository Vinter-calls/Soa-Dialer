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
    const nonStudents = await db.collection('users').find({
      role: { $ne: 'student' }
    }).project({ username: 1, email: 1, role: 1, center_id: 1, phone: 1, status: 1, password: 1 }).toArray();

    console.log(`Found ${nonStudents.length} non-student users in MongoDB:`);
    console.log(JSON.stringify(nonStudents, null, 2));
  } catch (err) {
    console.error("Error:", err);
  } finally {
    await client.close();
  }
}

main();
