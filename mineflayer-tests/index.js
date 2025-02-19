const mineflayer = require('mineflayer')
const { pathfinder, Movements, goals: { GoalNear } } = require('mineflayer-pathfinder')
const { Vec3 } = require('vec3')

const bot = mineflayer.createBot({
  host: 'localhost', // minecraft server ip
  username: 'Bot', // username to join as if auth is `offline`, else a unique identifier for this account. Switch if you want to change accounts
  auth: 'offline' // for offline mode servers, you can set this to 'offline'
  // port: 25565,              // set if you need a port that isn't 25565
  // version: false,           // only set if you need a specific version or snapshot (ie: "1.8.9" or "1.16.5"), otherwise it's set automatically
  // password: '12345678'      // set if you want to use password-based auth (may be unreliable). If specified, the `username` must be an email
})

const LEFT_CLICK = true
const RIGHT_CLICK = false

bot.loadPlugin(pathfinder)

bot.once('spawn', async () => {
  bot.chat("/clear")
  await sleep(500)
  bot.chat("/tp 10 56 0")
  await sleep(500)
  bot.chat("getn startd testin!")

  // move
  bot.chat("currently: moving")
  const defaultMove = new Movements(bot)
  const loc = { x: 10.5, y: 56, z: 8.5 }
  bot.pathfinder.setMovements(defaultMove)
  bot.chat("on my way!")
  await bot.pathfinder.goto(new GoalNear(loc.x, loc.y, loc.z, 0.25))
  await sleep(3000)

  // remove previous chest if it exists
  const chestToCleanup = bot.blockAt(new Vec3(10, 56, 10))
  if (bot.canDigBlock(chestToCleanup)){
    bot.chat(`starting to dig ${chestToCleanup.name}`)
    await bot.dig(chestToCleanup)
    bot.chat("removed existing chest...")
    await sleep(1000)
  }

  // place chest
  bot.chat("/give Bot minecraft:chest 1")
  await sleep(1000)
  const referenceBlock = bot.blockAt(new Vec3(10, 55, 10))
  await bot.placeBlock(referenceBlock, new Vec3(0, 1, 0))

  // interact with chest (shift click)
  await sleep(1000)
  bot.chat("/clear")
  await sleep(200)
  bot.chat("/give Bot minecraft:stone 1")
  const chest = bot.blockAt(new Vec3(10, 56, 10))
  bot.setControlState('sneak', true)
  await sleep(500)
  await clickOnChest(chest)
  await sleep(500)
  bot.chat("sell")
  await sleep(500)
  bot.chat("1")
  await sleep(500)
  bot.chat("1")
  await sleep(2000)


  const sign = bot.blockAt(new Vec3(10, 56, 9))
  if (sign.name.includes("sign")) {
    bot.chat("teh sign is there")
    const signText = sign.getSignText()
    console.log(signText[0])
    if (signText[0].includes("[shop]") && signText[0].includes("Selling") && signText[0].includes("1 Diamond(s)")){
      bot.chat("i did its! shop created :3")
      console.log("Successfully created sell shop")
    }
  } else {
    bot.chat("i failed my task :c")
  }
})

// Log errors and kick reasons:
bot.on('kicked', console.log)
bot.on('error', console.log)

async function clickOnChest(block) {
  bot._client.write('block_dig', {
    status: 0, // start digging
    location: block.position,
    face: 1
  })
  await sleep(100)
  bot._client.write('block_dig', {
    status: 1, // finish digging
    location: block.position,
    face: 1
  })
}

async function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}