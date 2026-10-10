# Deploying to an Oracle Cloud Always Free VM

1. Create an **Ampere A1** instance (up to 4 OCPU / 24 GB is free), Ubuntu 24.04. Pick Johannesburg (`af-johannesburg-1`) as home region if it has A1 capacity.
2. In the instance's VCN security list, add ingress rules for TCP **80, 443, 5000, 5001** from `0.0.0.0/0`.
3. Ubuntu images on OCI also block ports in iptables. On the VM:
   ```sh
   sudo iptables -I INPUT 6 -p tcp -m multiport --dports 80,443,5000,5001 -j ACCEPT
   sudo netfilter-persistent save
   ```
4. Install Docker: `curl -fsSL https://get.docker.com | sh && sudo usermod -aG docker $USER`, then log out and in.
5. Copy `docker-compose.yml` and `deploy/Caddyfile` to `~/switchlab/` (same layout), and create `~/switchlab/.env`:
   ```
   SWITCHLAB_DB_PASSWORD=<long random string>
   SWITCHLAB_DOMAIN=<your domain, or the VM's public IP for now>
   ```
6. `docker compose pull && docker compose up -d`
7. From your machine: `switchlab-client echo --host <vm-ip> --port 5001 --dialect nibss` should print an 0810 with field 39 = `00`.

The same steps work unchanged on any Ubuntu VPS (skip steps 2 and 3, open the ports in that provider's firewall instead).
