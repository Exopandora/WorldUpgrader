# World Upgrader
This mod upgrades worlds that were generated in older versions of Minecraft such that they look like they were generated in the most recent version of Minecraft.

## Supported Upgrades
This mod supports upgrading worlds with features that were introduced in the following versions.

### 1.21.5
#### Decorations
- Bushes
- Cactus flowers
- Dry grass
- Fallen trees
- Firefly bushes
- Leaf litter
- Tall dry grass
- Wildflowers

#### Entities
- Cow variants
- Chicken variants
- Pig variants
- Wolf variants

### 1.21.6
#### Structures
- Nether fossils (Dry Ghasts)

## How to upgrade a world
> [!WARNING]
> The upgrade process is not idempotent!

The set of upgrades that will be applied to a world can be controlled via the JVM argument `-Dworldupgrader.versions`.
It accepts a list of comma-separated Minecraft versions.
For example, the JVM argument `-Dworldupgrader.versions=1.21.5,1.21.6` will apply all upgrades for Minecraft versions 1.21.5 and 1.21.6.

1. Install [fabric-language-kotlin](https://github.com/FabricMC/fabric-language-kotlin/)
2. Build the mod `gradlew build`
3. Install the mod
4. Add the JVM argument explained above to your launcher profile or server command
5. Load the world you want to upgrade (this may take long time!)
6. Uninstall the mod
7. Remove the JVM argument from your launcher profile or server command
