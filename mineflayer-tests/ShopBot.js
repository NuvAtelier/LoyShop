const { Vec3 } = require('vec3')
const { pathfinder, Movements, goals: { GoalNear } } = require('mineflayer-pathfinder')
const BlockFaces = require('prismarine-world').iterators.BlockFace

// Constants for better readability
const TIMING = {
  SPAWN_DELAY: 2500,
  SHOP_COOLDOWN: 200,
  COMMAND_DELAY: 50,
  MOVEMENT_DELAY: 500,
  CHEST_INTERACTION_DELAY: 100,
  SHOP_CREATION_DELAY: 500
}

const POSITION = {
  START_X: -100,
  START_Y: 56,
  START_Z: 0,
  SHOP_SPACING: 2, // 1 block space between shops
  GRID_SIZE: 32,   // 32x32 grid of shops
  SIGN_OFFSET: 1,
  PLAYER_OFFSET: 1.5
}

// Shop types and items
const SHOP_TYPES = ["buy", "sell"]
const SHOP_ITEMS = [
  "stone",
  "gold_ingot",
  "diamond",
  "emerald",
  "lapis_lazuli",
  "redstone",
  "coal",
  "iron_ingot",
  "copper_ingot",
  "wheat",
  "carrot",
  "potato"
]

const TOOLS = {
  AXE: "netherite_axe[enchantments={levels:{efficiency:255}}]",
  CHEST: "chest"
}

async function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

class ShopBot {
  constructor(bot) {
    this.bot = bot
    this.defaultMove = null
    this.shopCount = 0
    this.maxShops = POSITION.GRID_SIZE * POSITION.GRID_SIZE

    this.bot.loadPlugin(pathfinder)
    this.bot.once('spawn', async () => {
      await sleep(TIMING.SPAWN_DELAY)
      await this.start()
    })

    this.setupEventListeners()
  }

  setupEventListeners() {
    this.bot.on('kicked', console.log)
    this.bot.on('error', console.log)
    // Commented code left for potential future use
    this.bot.on('message', (message) => {
      // console.log(message)
    })
  }

  /**
   * Creates a shop field in a grid pattern
   */
  async start() {
    const baseX = POSITION.START_X
    const y = POSITION.START_Y
    const baseZ = POSITION.START_Z

    // Create shops in a grid pattern
    for (let gridZ = 0; gridZ < POSITION.GRID_SIZE; gridZ++) {
      for (let gridX = 0; gridX < POSITION.GRID_SIZE; gridX++) {
        // Calculate actual position with proper spacing
        const x = baseX + (gridX * POSITION.SHOP_SPACING)
        const z = baseZ + (gridZ * POSITION.SHOP_SPACING)
        
        // Determine shop type and item based on position
        const shopType = this.getShopTypeForPosition(gridX, gridZ)
        const shopItem = this.getShopItemForPosition(gridX, gridZ)
        const quantity = 1
        const price = 1
        
        // Create shop and update progress
        await this.createShopRoutine(shopItem, shopType, quantity, price, x, y, z)
        this.shopCount++
        
        // Log progress
        if (this.shopCount % 10 === 0) {
          console.log(`Progress: ${this.shopCount}/${this.maxShops} shops created (${Math.floor(this.shopCount/this.maxShops*100)}%)`)
        }
      }
    }
    
    console.log(`Completed: Created ${this.shopCount} shops in a ${POSITION.GRID_SIZE}x${POSITION.GRID_SIZE} grid`)
  }

  /**
   * Determines shop type (buy/sell) based on position
   */
  getShopTypeForPosition(x, z) {
    // Alternate between buy and sell in a checkerboard pattern
    return SHOP_TYPES[(x + z) % 2]
  }
  
  /**
   * Determines shop item based on position
   */
  getShopItemForPosition(x, z) {
    // Create a pattern of items
    const itemIndex = (x + z * POSITION.GRID_SIZE) % SHOP_ITEMS.length
    return SHOP_ITEMS[itemIndex]
  }

  async createShopRoutine(item, type, quantity, price, x, y, z) {
    await this.clearInventory()
    await this.moveToLocation(x, y, z - POSITION.PLAYER_OFFSET)
    await this.giveItem(TOOLS.AXE)
    
    // Make sure the bot has loaded the area
    await sleep(TIMING.COMMAND_DELAY * 2)
    
    // Clear existing blocks
    await this.removeBlockIfExists(x, y, z - POSITION.SIGN_OFFSET, BlockFaces.WEST) // Remove sign
    await this.removeBlockIfExists(x, y, z, BlockFaces.WEST) // Remove chest
    
    // Set up and create the shop
    await this.placeChest(x, y - 1, z)
    await this.giveItem(item)
    await this.createShop(type, quantity, price, x, y, z)
    await this.verifyShopCreation(x, y, z - POSITION.SIGN_OFFSET)
    
    // Wait for shop cooldown
    await sleep(TIMING.SHOP_COOLDOWN)
  }

  async clearInventory() {
    this.bot.chat("/clear")
    await sleep(TIMING.COMMAND_DELAY)
  }

  async giveItem(item) {
    await this.clearInventory()
    this.bot.chat(`/give Bot minecraft:${item} 1`)
    await sleep(TIMING.COMMAND_DELAY)
  }

  async moveToLocation(x, y, z) {
    this.bot.chat(`/tp ${x} ${y} ${z}`)
    await sleep(TIMING.MOVEMENT_DELAY)
    
    // Pathfinding implementation kept for potential future use
    /*
    if (!this.defaultMove) {
      this.defaultMove = new Movements(this.bot)
    }
    this.bot.pathfinder.setMovements(this.defaultMove)
    await this.bot.pathfinder.goto(new GoalNear(x, y, z, 0.25))
    await sleep(1500)
    */
  }

  async removeBlockIfExists(x, y, z, face) {
    const blockToCleanup = this.bot.blockAt(new Vec3(x, y, z))
    if (this.bot.canDigBlock(blockToCleanup)) {
      this.bot.chat(`starting to dig ${blockToCleanup.name}`)
      await this.bot.dig(blockToCleanup, true, face)
      this.bot.chat("removed existing block...")
      await sleep(TIMING.COMMAND_DELAY)
    }
  }

  async placeChest(x, y, z) {
    await this.giveItem(TOOLS.CHEST)
    
    // Ensure there's a reference block by placing a temporary block if needed
    const blockPos = new Vec3(x, y, z)
    let referenceBlock = this.bot.blockAt(blockPos)
    
    if (!referenceBlock || referenceBlock.name === 'air') {
      // First, we need to check if we're in a loaded chunk
      if (!this.bot.world.getColumnAt(blockPos)) {
        console.log(`Chunk at (${x}, ${z}) not loaded, waiting...`)
        await sleep(1000) // Wait for chunk to load
        
        // If still not loaded, we can force load it with a teleport nearby
        if (!this.bot.world.getColumnAt(blockPos)) {
          this.bot.chat(`/tp ${x} ${y} ${z}`)
          await sleep(1000)
        }
      }
      
      // Try to place a temporary block below
      this.bot.chat(`/setblock ${x} ${y-1} ${z} stone`)
      await sleep(TIMING.COMMAND_DELAY * 2)
      
      // Update reference block
      referenceBlock = this.bot.blockAt(blockPos)
      if (!referenceBlock || referenceBlock.name === 'air') {
        console.log(`Failed to get reference block at (${x}, ${y}, ${z}), chest placement may fail`)
        // Last resort - try to use a different placement method
        this.bot.chat(`/setblock ${x} ${y} ${z} chest`)
        await sleep(TIMING.COMMAND_DELAY)
        return
      }
    }
    
    // Now place the chest using the reference block
    try {
      await this.bot.placeBlock(referenceBlock, new Vec3(0, 1, 0))
    } catch (err) {
      console.log(`Error placing chest: ${err.message}`)
      // Fallback to setblock command
      this.bot.chat(`/setblock ${x} ${y} ${z} chest`)
    }
    
    await sleep(TIMING.COMMAND_DELAY)
  }

  async createShop(type, quantity, price, x, y, z) {
    const chest = this.bot.blockAt(new Vec3(x, y, z))
    
    // Interact with chest while sneaking
    this.bot.setControlState('sneak', true)
    await sleep(TIMING.COMMAND_DELAY)
    await this.clickOnChest(chest)
    await sleep(TIMING.COMMAND_DELAY)
    this.bot.setControlState('sneak', false)
    await sleep(TIMING.COMMAND_DELAY)
    
    // Input shop parameters
    this.bot.chat(type)
    await sleep(TIMING.COMMAND_DELAY)
    this.bot.chat(`${quantity}`)
    await sleep(TIMING.COMMAND_DELAY)
    this.bot.chat(`${price}`)
    await sleep(TIMING.SHOP_CREATION_DELAY)
  }

  async verifyShopCreation(x, y, z) {
    const sign = this.bot.blockAt(new Vec3(x, y, z))
    if (sign.name.includes("sign")) {
      this.bot.chat("The sign is there")
      const signText = sign.getSignText()
      console.log(signText[0])
      
      if (signText[0].includes("[shop]")) {
        this.bot.chat("Shop created successfully")
        console.log("Successfully created shop")
        return true
      }
    } else {
      this.bot.chat("Shop creation failed")
      return false
    }
  }

  // Left clicks on a block
  async clickOnChest(block) {
    this.bot._client.write('block_dig', {
      status: 0, // start digging
      location: block.position,
      face: 1
    })
    await sleep(TIMING.CHEST_INTERACTION_DELAY)
    this.bot._client.write('block_dig', {
      status: 1, // cancel digging
      location: block.position,
      face: 1
    })
  }
}

module.exports = ShopBot;
