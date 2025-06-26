# World Upgrader
This mod upgrades worlds that were generated in older versions of Minecraft such that they look like they were generated in the most recent version of Minecraft.

## Supported Upgrades
This mod currently only supports upgrading worlds with features that were introduced in 1.21.5.

### Decorations
- Bushes
- Cactus flowers
- Dry grass
- Fallen trees
- Firefly bushes
- Leaf litter
- Tall dry grass
- Wildflowers

### Entities
- Cow variants
- Chicken variants
- Pig variants
- Wolf variants

## How to upgrade a world
> [!WARNING]
> The upgrade process is not idempotent!

1. Install [fabric-language-kotlin](https://github.com/FabricMC/fabric-language-kotlin/)
2. Build the mod `gradlew build`
3. Install the mod
4. Add the jvm argument `-Dworldupgrader.versions=1.21.5` to your launcher profile or server script
5. Load the world you want to upgrade (this may take a while!)
6. Uninstall the mod
7. Remove the jvm argument from your launcher profile or server script
