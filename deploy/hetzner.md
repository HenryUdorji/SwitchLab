# Deploying to Hetzner Cloud

1. In the Hetzner Cloud console, create a server: **Ubuntu 24.04**, the smallest ARM (`CAX`) or x86 (`CX`) type with 4 GB RAM, location Falkenstein, Nuremberg or Helsinki. Add your SSH key.
2. Create a **Firewall** and attach it to the server, with inbound TCP rules:
   - `22` from your own IP
   - `80`, `443`, `5000`, `5001` from anywhere
3. Optionally enable **Backups** on the server (daily snapshots, 7 kept).
4. SSH in and install Docker: `curl -fsSL https://get.docker.com | sh`
5. Copy `docker-compose.yml` and `deploy/Caddyfile` to `~/switchlab/` (same layout), and create `~/switchlab/.env`:
   ```
   SWITCHLAB_DB_PASSWORD=<long random string>
   SWITCHLAB_DOMAIN=<your domain, or the server's public IP for now>
   ```
6. `cd ~/switchlab && docker compose pull && docker compose up -d`
7. From your machine: `switchlab-client echo --host <server-ip> --port 5001 --dialect nibss` should print an 0810 with field 39 = `00`.

Nothing here is Hetzner-specific apart from steps 1 to 3. Any Ubuntu VPS works the same way.
