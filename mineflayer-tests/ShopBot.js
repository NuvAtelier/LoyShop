const { Vec3 } = require('vec3')
const { pathfinder, Movements, goals: { GoalNear } } = require('mineflayer-pathfinder')
const BlockFaces = require('prismarine-world').iterators.BlockFace

async function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

class ShopBot {
    constructor(bot) {
        this.bot = bot
        this.defaultMove

        this.bot.loadPlugin(pathfinder)
        this.bot.once('spawn', async () => {
            await sleep(2500)
            await this.start()
        })

        this.bot.on('kicked', console.log)
        this.bot.on('error', console.log)

        this.bot.on('message', (message) => {
            // console.log(message)
        })
    }

    async start() {
        let x = 10
        let y = 56
        let z = 10
        await this.createShopRoutine("stone", "sell", 1, 1, x, y, z)
        x += 2
        await this.createShopRoutine("gold_ingot", "buy", 1, 1, x, y, z)
        x += 2
        await this.createShopRoutine("diamond", "sell", 1, 1, x, y, z)
        x += 2
        await this.createShopRoutine("emerald", "buy", 1, 1, x, y, z)
        x += 2
        await this.createShopRoutine("lapis_lazuli", "sell", 1, 1, x, y, z)
        x += 2
        await this.createShopRoutine("redstone", "buy", 1, 1, x, y, z)
        x += 2
        await this.createShopRoutine("coal", "sell", 1, 1, x, y, z)
        
    }

    async createShopRoutine(item, type, quantity, price, x, y, z) {
        await this.clearInventory()
        await this.moveToLocation(x, y, z - 1.5)
        await this.giveItem("netherite_axe[enchantments={levels:{efficiency:255}}]")
        await this.removeBlockIfExists(x, y, z - 1, BlockFaces.WEST) // Remove sign
        await this.removeBlockIfExists(x, y, z, BlockFaces.WEST) // Remove chest
        await this.placeChest(x, y - 1, z) // Place chest
        await this.giveItem(item)
        await this.createShop(type, quantity, price, x, y, z) // Create shop
        await this.doesSignExist(x, y, z - 1) // Check if shop was created
        await sleep(3500) // 5 second cooldown between creating shops
    }

    async clearInventory() {
        this.bot.chat("/clear")
        await sleep(50)
    }

    async giveItem(item) {
        await this.clearInventory()
        this.bot.chat(`/give Bot minecraft:${item} 1`)
        await sleep(50)
    }

    async moveToLocation(x, y, z) {
        this.bot.chat(`/tp ${x} ${y} ${z}`)
        await sleep(500)
        // if (!this.defaultMove) {
        //     this.defaultMove = new Movements(this.bot)
        // }
        // this.bot.chat("currently: moving")
        // this.bot.pathfinder.setMovements(this.defaultMove)
        // this.bot.chat("on my way!")
        // await this.bot.pathfinder.goto(new GoalNear(x, y, z, 0.25))
        // await sleep(1500)
    }

    async removeBlockIfExists(x, y, z, face) {
        // remove previous chest if it exists
        const blockToCleanup = this.bot.blockAt(new Vec3(x, y, z))
        if (this.bot.canDigBlock(blockToCleanup)){
            this.bot.chat(`starting to dig ${blockToCleanup.name}`)
            await this.bot.dig(blockToCleanup, true, face)
            this.bot.chat("removed existing block...")
            await sleep(50)
        }
    }

    async placeChest(x, y, z) {
        await this.giveItem("chest")
        const referenceBlock = this.bot.blockAt(new Vec3(x, y, z))
        await this.bot.placeBlock(referenceBlock, new Vec3(0, 1, 0))
        await sleep(50)
    }

    async createShop(type, quantity, price, x, y, z) {
        const chest = this.bot.blockAt(new Vec3(x, y, z))
        this.bot.setControlState('sneak', true)
        await sleep(50)
        await this.clickOnChest(chest)
        await sleep(50)
        this.bot.setControlState('sneak', false)
        await sleep(50)
        this.bot.chat(type)
        await sleep(50)
        this.bot.chat(`${quantity}`)
        await sleep(50)
        this.bot.chat(`${price}`)
        await sleep(500)
    }

    async doesSignExist(x, y, z) {
        const sign = this.bot.blockAt(new Vec3(x, y, z))
        if (sign.name.includes("sign")) {
            this.bot.chat("teh sign is there")
            const signText = sign.getSignText()
            console.log(signText[0])
            if (signText[0].includes("[shop]")){
                this.bot.chat("i did its! shop created :3")
                console.log("Successfully created sell shop")
                return true
            }
        } else {
            this.bot.chat("i failed my task :c")
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
        await sleep(100)
        this.bot._client.write('block_dig', {
          status: 1, // cancel digging
          location: block.position,
          face: 1
        })
      }
}

module.exports = ShopBot;
