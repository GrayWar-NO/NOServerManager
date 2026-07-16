# Graywar's Nuclear Option Server Manager
# Central service

The central service is the cornerstone of this management suite. It should be started first and stopped last in any
deployment.
All other parts of the suite can continue running without the central, but the integrity of the logs is not guaranteed
in that case.

## Configuring

### DB

This part of config is relatively self-explanatory. Make sure to match this with whatever you put in the docker compose
if you
use that.

### Discord

The discord bot only works on one server (guild) at once. That's what the guildID defines.

The serverWebhooks property is a list of publicChat/privateChat couples. Each couple corresponds to a server, in order
of their database IDs. PublicChat only logs messages sent to all chat, when private logs all messages, including
whispers.

teamKillWebhook is the webhook that will recieve all the teamkill reports.

adminRoles are all the roles that are allowed to run admin commands in Discord (`/command`, `/newserver`, `/newmission`
and `/servers`).

LinkedRole is the role that will be attributed to all players that have linked their discord account to their steamID
via the `/linkme` command.

### API

The API is an experimental feature (I should implement an enable switch for it tbh)

It doesnt really matter what you put in there, it wont be exposed without special setup, and it does basically nothing
either way unless you know what you are doing.

## Deploying a new central

In order to deploy a central, you will need

- a directory containing:
    - a `central.conf` file
    - a `CA` subdirectory.
    - the `central-compose.yml` file from this repo (edit at your convenience)
- A discord server with:
    - A bot you have the token to on it.
    - The ability to [create and edit webhooks](#discord)

In the `CA` subdir, you will need to create a CA authority (that's what `newCA.sh` is for; use the central.conf file in
there to configure it).
Once that is done, create a server certificate with `newServerCert.sh`.

You will also need the docker image for the central. The provided `central-compose.yml` file already points to our
latest release on docker hub.

Once your central is configured and you only need to `docker compose up -d`!

## Deploying a new Nuclear Option server

1. Make sure you [have a working central](#deploying-a-new-central)
2. Add the [required webhooks](#discord) in the central's config and restart it.
3. In a folder, place the edge-compose.yml, a `config` folder (that's the config for
   the [server image](https://github.com/GrayWar-NO/Docker_server)), and an
   `CA` folder.
4. Grab a copy of the [GrayWar server plugin](https://github.com/GrayWar-NO/GrayWar-Server-Plugin). You might need to
   build it manually if I have not made any releases. Make sure to check for branches that would be more up-to-date than
   `main`.
5. Run your server once to generate the config. It will be located in `$SERVER_DIR/config/BepInEx/config`.
   You will then need to [configure the server](#configuring-a-server).
6. On your central, in the `CA` folder, generate a new set of keys with the name you gave your server at step 5.
7. Go back to your game server, and place a copy of `ca.crt`, `servername.crt` and `servername.key` in the `CA` folder.
8. `docker compose up -d`
9. enjoy!

## Configuring a server

The GrayWar server plugin configuration is quite long and extensive. You can look through it and configure it as you wish. Here are the required config options to start a new server.

Scroll to the gRPC interface section. `server name` is the name that your server will appear as in  the DB, and the name of the certificate files it will look for.

This is the only strictly required setting. Feel free to explore the config and configure your server as you desire.
