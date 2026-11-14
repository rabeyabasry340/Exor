Exor Media Server - full project directory (v0.2.0)

Package: com.exor.mediaserver

Features implemented:
- DLNA / UPnP media server (videos, music, photos from MediaStore)
- SSDP discovery + ContentDirectory + streaming
- Device scan for TVs / receivers
- Floating window
- Termux bridge (install, open terminal, run Python)
- Workspace: pick .html → host on IP:8080 with password login (default Frozen)
            pick Flask .py → execute via Termux
- Hamburger menu (top-left 3 lines) → Home / Workspace / Open terminal
- Foreground notifications: "DLNA running" / "Server running"
- First-launch asks Notification + Storage; overlay asked on Floating window

Build: see guidetobuild.txt
Drop Termux APK into app/src/main/assets/termux/
