#!/usr/bin/env bash
# Start backend. I Termux: pkg install python python-pydantic && pip install fastapi uvicorn
cd "$(dirname "$0")"
exec python -m uvicorn app.main:app --host "${HOST:-0.0.0.0}" --port "${PORT:-8000}"
