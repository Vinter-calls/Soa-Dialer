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
    const roles = await db.collection('users').distinct('role');
    console.log("Distinct roles in 'users':", roles);

    const managers = await db.collection('users').find({
      role: { $regex: /manager/i }
    }).project({ username: 1, email: 1, role: 1, center_id: 1, phone: 1, status: 1, password: 1 }).toArray();
    console.log(`\nFound ${managers.length} manager users:`);
    console.log(JSON.stringify(managers, null, 2));

    const centers = await db.collection('centers').find({}).toArray();
    console.log(`\nCenters (${centers.length}):`);
    console.log(JSON.stringify(centers.map(c => ({ id: c._id.toString(), name: c.center_name, location: c.location })), null, 2));
  } catch (err) {
    console.error("Error:", err);
  } finally {
    await client.close();
  }
}

main();
