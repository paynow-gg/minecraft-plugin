# PayNow Plugin for Minecraft

The official PayNow addon seamlessly integrates with Minecraft servers, facilitating in-game transactions and enhancing gameplay with secure and efficient financial interactions. This guide will help you set up the PayNow plugin on your Minecraft server.

## Prerequisites

Ensure you have administrative access to your Minecraft server and the ability to modify its configuration.

## Configuration

After the plugin is automatically installed and configured for the first time, you may want to customize its settings, such as the server connection token and the command fetch interval.

### Setting Your Token

To connect your server with the PayNow gameserver, set your unique PayNow token using the following server command:

```plaintext
/paynow link <token>
```

Replace `<token>` with your actual PayNow token.

### Adjusting Fetch Interval

The default fetch interval is recommended for most servers, but you can adjust it to meet your specific needs by modifying the config.

## Support

For support, questions, or more information, join our Discord community:

- [Discord](https://discord.gg/paynow)

## Contributing

Contributions are welcome! If you'd like to improve the PayNow plugin or suggest new features, please fork the repository, make your changes, and submit a pull request.

## Download
To download the plugins please visit our release page: [Download](https://github.com/paynow-gg/minecraft-plugin/releases/latest)

## Modules

You can find all our modules if you wish to modify them yourself below.
- [paynow-bukkit](./paynow-bukkit)
- [paynow-bungeecord](./paynow-bungeecord)
- [paynow-lib](./paynow-lib)
- [paynow-sponge](./paynow-sponge)
- [paynow-velocity](./paynow-velocity)
- [paynow-fabric](./paynow-fabric)
