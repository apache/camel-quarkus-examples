# Acme Sync Product Guide

Acme Sync is the desktop client that keeps a local folder synchronised with your Acme cloud
workspace. This guide covers installation, activation, the first synchronisation and the most
common problems.

## System requirements

Acme Sync runs on Windows 10 and later, macOS 13 and later, and Ubuntu 22.04 and later. The client
needs 4 GB of memory, 500 MB of disk space for the application and outbound HTTPS access to
sync.acme.example on port 443. No inbound connection is required.

## Installation

On Windows, run the downloaded installer and follow the wizard; administrator rights are required
once, for the installation itself. On macOS, open the disk image and drag Acme Sync to the
Applications folder. On Ubuntu, install the .deb package with `sudo apt install ./acme-sync.deb`.
The client starts automatically at login after the installation.

## Licensing and activation

Acme Sync is included in every Team and Enterprise subscription. On first start, sign in with your
Acme account: the licence is checked online and cached for 30 days, so the client keeps working
while you are offline. A licence can be active on three devices at the same time; a fourth device
signs out the oldest one.

## The initial synchronisation

The first synchronisation downloads the whole workspace, which can take a while for large
workspaces. Progress is shown in the tray icon menu. Choose "Selective sync" in the preferences to
download only some folders; folders left out remain visible online. Files are checked every
30 seconds, and a change made on another device usually appears within a minute.

## Troubleshooting

### Slow synchronisation and high CPU usage without error codes

When users can sign in but synchronisation is extremely slow and the client consumes a lot of CPU,
with no error codes in the log, the cause is nearly always the network path rather than the client.
The two configurations to check are:

- A proxy or security appliance that inspects TLS traffic on port 443. Acme Sync detects the
  replaced certificate and falls back to a per-file verification mode, which is slow and CPU
  intensive. Add sync.acme.example to the proxy bypass list so the traffic is not inspected.
- A VPN link with a reduced MTU. Fragmented packets make the client retransmit large chunks
  repeatedly. Set the MTU on the VPN adapter to 1400 or lower, or enable "Small chunks" in the
  advanced preferences of the client.

After changing either setting, restart the client. Synchronisation returns to normal speed and the
CPU usage drops within a few minutes.

### The client cannot reach the server

If the tray icon shows "Offline" although the network works, verify that outbound HTTPS to
sync.acme.example on port 443 is allowed by the firewall. The client does not support SOCKS proxies;
use an HTTP proxy configured in the preferences.

### Log files

Logs are written to the "logs" folder inside the application data directory. Choose "Collect
diagnostics" in the tray icon menu to create an archive with the logs and the current configuration,
and attach it when contacting support.

## Updates

Updates are downloaded automatically and installed at the next start of the client. Enterprise
administrators can pin a version and roll out updates through the admin console.
