#!/usr/bin/env python3
"""Run the SRTgo web backend server."""

import argparse
import uvicorn


def main():
    parser = argparse.ArgumentParser(description="SRTgo Web Backend")
    parser.add_argument("--host", default="0.0.0.0", help="Host to bind (default: 0.0.0.0)")
    parser.add_argument("--port", type=int, default=8000, help="Port to bind (default: 8000)")
    parser.add_argument("--reload", action="store_true", help="Enable auto-reload for development")
    args = parser.parse_args()

    import sys
    print(f"\n[SRTgo Web] JWT Auth enabled", flush=True)
    print(f"[SRTgo Web] Server: http://{args.host}:{args.port}\n", flush=True)
    sys.stdout.flush()

    uvicorn.run(
        "web.backend.main:app",
        host=args.host,
        port=args.port,
        reload=args.reload,
    )


if __name__ == "__main__":
    main()
