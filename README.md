# Time Debt

A Minecraft Fabric mod that introduces consequences for skipping the night. Every time you sleep in a bed, you accumulate Time Debt. The more debt you accumulate, the harsher the penalties become.

## Features

### Debt Accumulation
- Each time you sleep through the night, your Time Debt increases by 1 point
- Debt is tracked per-player

### Escalating Penalties

| Debt Level | Effect |
|------------|--------|
| 3+ | Mining speed reduced by 20% |
| 5+ | Shadow phantoms may spawn during the day |
| 7+ | Vision becomes obscured with a dark vignette effect |

### Debt Reduction
- Stay awake through a complete day-night cycle (20 minutes real time) to reduce your debt by 1 point
- Debt cannot go below 0

## Commands

- `/timedebt` - Check your current debt level and active penalties

## How It Works

The mod tracks when players sleep in beds. When you wake up after sleeping through the night, your Time Debt increases. The game monitors your playtime, and if you remain awake through an entire day-night cycle without sleeping, your debt decreases.

The shadow mobs that spawn at high debt levels are phantom-like creatures that appear during daylight hours - a manifestation of the time you've stolen from the natural cycle.

## Requirements

- Minecraft 1.21.1
- Fabric Loader 0.16.0+
- Fabric API

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.21.1
2. Download and install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Download the Time Debt JAR file from the releases
4. Place the JAR file in your `mods` folder
5. Launch Minecraft with the Fabric profile

## Building from Source

```bash
git clone https://github.com/Simplifine-gamedev/time-debt.git
cd time-debt
./gradlew build
```

The built JAR will be in `build/libs/`.

## Gameplay Tips

- If you need to mine efficiently, consider staying awake for a cycle first
- The darkness effect at 7+ debt makes navigation dangerous - watch your step
- Shadow phantoms can attack during the day, so keep your guard up if your debt is high
- Balance your desire to skip nights with the consequences of Time Debt

## License

MIT License
