#!/usr/bin/env bash
set -e

# Materialy Music — быстрый старт без Docker + Cloudflare Quick Tunnel
# Использование: ./start_server.sh [--port 8000] [--no-tunnel] [--expose]
# Логи: /tmp/materialy_uvicorn.log, /tmp/materialy_cloudflared.log
# PID: /tmp/materialy_uvicorn.pid, /tmp/materialy_cloudflared.pid

PORT=8000
USE_TUNNEL=1
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SERVER_DIR="$SCRIPT_DIR/server"
VENV_DIR="$SERVER_DIR/.venv"
LOG_UVICORN="/tmp/materialy_uvicorn.log"
LOG_CLOUDFLARED="/tmp/materialy_cloudflared.log"
PID_UVICORN="/tmp/materialy_uvicorn.pid"
PID_CLOUDFLARED="/tmp/materialy_cloudflared.pid"

for arg in "$@"; do
  case $arg in
    --port) shift; PORT=$1; shift;;
    --port=*) PORT="${arg#*=}";;
    --no-tunnel) USE_TUNNEL=0;;
    --expose) USE_TUNNEL=1;;
    -h|--help)
      echo "Usage: $0 [--port 8000] [--no-tunnel]"
      echo "  --port N     порт сервера (default 8000)"
      echo "  --no-tunnel  не запускать cloudflared"
      exit 0;;
  esac
done

echo "== Materialy Music Server quick start =="
echo "SCRIPT_DIR: $SCRIPT_DIR"
echo "SERVER_DIR: $SERVER_DIR"
echo "PORT: $PORT  TUNNEL: $USE_TUNNEL"

# 0. Убить старые процессы
if [ -f "$PID_UVICORN" ]; then
  OLD=$(cat "$PID_UVICORN" 2>/dev/null || true)
  if kill -0 "$OLD" 2>/dev/null; then echo "Убиваю старый uvicorn $OLD"; kill "$OLD" || true; sleep 1; fi
  rm -f "$PID_UVICORN"
fi
if [ -f "$PID_CLOUDFLARED" ]; then
  OLD=$(cat "$PID_CLOUDFLARED" 2>/dev/null || true)
  if kill -0 "$OLD" 2>/dev/null; then echo "Убиваю старый cloudflared $OLD"; kill "$OLD" || true; sleep 1; fi
  rm -f "$PID_CLOUDFLARED"
fi
pkill -f "uvicorn app:app --host 0.0.0.0 --port $PORT" 2>/dev/null || true
pkill -f "cloudflared tunnel --url http://localhost:$PORT" 2>/dev/null || true

# 1. Проверка зависимостей
if ! command -v python3 >/dev/null; then echo "ERROR: python3 не найден"; exit 1; fi
if [ ! -f "$SERVER_DIR/app.py" ]; then echo "ERROR: $SERVER_DIR/app.py не найден"; exit 1; fi
if [ ! -f "$SERVER_DIR/requirements.txt" ]; then echo "ERROR: requirements.txt не найден"; exit 1; fi

if ! command -v ffmpeg >/dev/null; then
  echo "WARN: ffmpeg не установлен — транскод в mp3 320k будет недоступен, будет native m4a/opus. Установи: sudo apt install ffmpeg"
else
  echo "OK: ffmpeg $(ffmpeg -version 2>&1 | head -n1)"
fi

# 2. Venv + pip
if [ ! -d "$VENV_DIR" ] || [ ! -f "$VENV_DIR/bin/activate" ]; then
  echo "== Создаю venv $VENV_DIR =="
  # --without-pip чтобы работал без python3-venv pip, потом bootstrap
  python3 -m venv "$VENV_DIR" --without-pip 2>&1 || python3 -m venv "$VENV_DIR" 2>&1 || {
    echo "ERROR: не удалось создать venv. Ставлю напрямую через pip --break-system-packages"
    VENV_DIR=""
  }
fi

if [ -n "$VENV_DIR" ] && [ -f "$VENV_DIR/bin/python" ]; then
  PY="$VENV_DIR/bin/python"
  PIP="$VENV_DIR/bin/pip"
  # bootstrap pip если нет
  if [ ! -f "$PIP" ]; then
    echo "== Ставлю pip в venv =="
    curl -sS https://bootstrap.pypa.io/get-pip.py | "$PY" 2>&1 | tail -n5
  fi
else
  echo "WARN: venv недоступен, использую системный python3 + --break-system-packages"
  PY="python3"
  PIP="pip --break-system-packages"
  # обеспечить pip
  if ! python3 -m pip --version >/dev/null 2>&1; then
    echo "== Ставлю pip системно =="
    curl -sS https://bootstrap.pypa.io/get-pip.py | python3 --break-system-packages 2>&1 | tail -n5
  fi
fi

echo "== Устанавливаю зависимости =="
if [ -n "$VENV_DIR" ]; then
  "$PIP" install -q -r "$SERVER_DIR/requirements.txt" 2>&1 | tail -n20
else
  python3 -m pip install --break-system-packages -q -r "$SERVER_DIR/requirements.txt" 2>&1 | tail -n20
fi

# 3. Запуск uvicorn
echo "== Запускаю uvicorn на 0.0.0.0:$PORT =="
# ensure downloads dir
mkdir -p /tmp/materialy_downloads
# ensure node / bun in PATH for yt-dlp JS challenge solver
if [ -d "$HOME/.nvm/versions/node" ]; then
  LATEST_NODE=$(ls -d "$HOME/.nvm/versions/node"/* 2>/dev/null | tail -n1)
  if [ -n "$LATEST_NODE" ]; then export PATH="$LATEST_NODE/bin:$PATH"; fi
fi
if [ -d "$HOME/.bun/bin" ]; then export PATH="$HOME/.bun/bin:$PATH"; fi

if [ -n "$VENV_DIR" ]; then
  setsid "$VENV_DIR/bin/uvicorn" app:app --host 0.0.0.0 --port "$PORT" --app-dir "$SERVER_DIR" >"$LOG_UVICORN" 2>&1 < /dev/null &
else
  setsid python3 -m uvicorn app:app --host 0.0.0.0 --port "$PORT" --app-dir "$SERVER_DIR" >"$LOG_UVICORN" 2>&1 < /dev/null &
fi
UV_PID=$!
disown $UV_PID 2>/dev/null || true
echo $UV_PID > "$PID_UVICORN"
echo "uvicorn PID $UV_PID, лог $LOG_UVICORN"

# ждать health
echo -n "Жду health..."
for i in $(seq 1 20); do
  if curl -s "http://127.0.0.1:$PORT/health" | grep -q '"status":"ok"'; then
    echo " OK"
    break
  fi
  sleep 0.5
  echo -n "."
  if [ $i -eq 20 ]; then echo " FAIL"; echo "Лог:"; tail -n50 "$LOG_UVICORN"; exit 1; fi
done

LAN_IP=$(hostname -I 2>/dev/null | awk '{print $1}')
if [ -z "$LAN_IP" ]; then LAN_IP="192.168.1.100"; fi

echo ""
echo "✅ Сервер поднят:"
echo "   Local:   http://127.0.0.1:$PORT"
echo "   LAN:     http://$LAN_IP:$PORT  <- вставь в Android Settings -> Server URL"
echo "   Health:  curl http://127.0.0.1:$PORT/health"
echo "   Docs:    http://127.0.0.1:$PORT/docs"
echo ""

# 4. Cloudflare Quick Tunnel
if [ "$USE_TUNNEL" -eq 1 ]; then
  echo "== Запускаю Cloudflare Quick Tunnel =="
  CF_BIN=""
  if command -v cloudflared >/dev/null 2>&1; then CF_BIN=$(command -v cloudflared)
  elif [ -f "$SCRIPT_DIR/cloudflared" ]; then CF_BIN="$SCRIPT_DIR/cloudflared"
  elif [ -f "/tmp/cloudflared" ]; then CF_BIN="/tmp/cloudflared"
  else
    echo "Скачиваю cloudflared..."
    ARCH=$(uname -m)
    case $ARCH in
      x86_64) CF_URL="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64" ;;
      aarch64|arm64) CF_URL="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm64" ;;
      *) CF_URL="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64" ;;
    esac
    curl -L -o /tmp/cloudflared "$CF_URL" 2>&1 | tail -n5
    chmod +x /tmp/cloudflared
    CF_BIN="/tmp/cloudflared"
  fi

  echo "cloudflared: $CF_BIN"
  # quick tunnel не требует логина
  setsid "$CF_BIN" tunnel --url "http://localhost:$PORT" >"$LOG_CLOUDFLARED" 2>&1 < /dev/null &
  CF_PID=$!
  disown $CF_PID 2>/dev/null || true
  echo $CF_PID > "$PID_CLOUDFLARED"
  echo "cloudflared PID $CF_PID, лог $LOG_CLOUDFLARED"
  echo -n "Жду URL туннеля..."
  TUNNEL_URL=""
  for i in $(seq 1 30); do
    # cloudflared пишет url в лог: https://xxxx.trycloudflare.com
    TUNNEL_URL=$(grep -oE "https://[a-z0-9-]+\.trycloudflare\.com" "$LOG_CLOUDFLARED" 2>/dev/null | head -n1)
    if [ -n "$TUNNEL_URL" ]; then echo " OK"; break; fi
    sleep 1
    echo -n "."
    if [ $i -eq 30 ]; then echo " WARN: не дождался URL, смотри лог $LOG_CLOUDFLARED"; break; fi
  done

  if [ -n "$TUNNEL_URL" ]; then
    echo ""
    echo "🌍 Cloudflare Tunnel:"
    echo "   Public:  $TUNNEL_URL  <- вставь в Android если не дома"
    echo "   Проверка: curl $TUNNEL_URL/health"
    echo ""
  else
    echo "Лог cloudflared:"
    tail -n50 "$LOG_CLOUDFLARED" || true
  fi
else
  echo "Туннель отключен (--no-tunnel). Для доступа извне запусти: ./start_server.sh --expose или Tailscale."
fi

echo ""
echo "== Готово =="
echo "Логи: tail -f $LOG_UVICORN  |  tail -f $LOG_CLOUDFLARED"
echo "Остановить: kill \$(cat $PID_UVICORN) \$(cat $PID_CLOUDFLARED) 2>/dev/null; rm $PID_UVICORN $PID_CLOUDFLARED"
echo "Тест: curl http://127.0.0.1:$PORT/info?url=https://soundcloud.com/lakeyinspired/chill-day"
