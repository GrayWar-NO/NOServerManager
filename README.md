# Graywar's Nuclear Option Server Manager

This server manager comes in 2 main parts: A [central service](#Central-service), that handles connecting to the
database and the creation of the discord bot and the API,
and one [edge agent](#edge-agent) per server.

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

# Edge agent

The edge agent is here to manage the nuclear option server at the edge.
It connects to it via TCP, and manages encryption to transmit data back to the central, as well as limited data
processing.

## Configuring

- Name: This is the name you will give your server. It MUST be the same as the one in the database and on the
- nuclearOption: This is the configuration for the remote connection to the game server. If you're using compose, I
  advise leaving it as-is.
- the config for your central server's hostname and port.

## Deploying a new Nuclear Option server

1. Make sure you [have a working central](#deploying-a-new-central)
2. Add a server to your database, either manually or with `/newserver` in discord, and take note of it's ID  (it is
   incremental)
3. Add the [required webhooks](#discord) in the central's config and restart it.
4. In a folder, place the edge-compose.yml, a `config` folder (that's the config for the [server image](https://github.com/GrayWar-NO/Docker_server)), and an
   `edge-agent` folder.
5. In the `CA` folder of your central, generate a new set of keys with the name you gave your server at step 2.
6. In the `edge-agent` folder, you will need to place your `edge-agent.conf` file, and a `CA` folder containing a copy
   of `ca.crt` as well as `servername.crt` and `servername.key`
7. `docker compose up -d`
8. enjoy!

 

