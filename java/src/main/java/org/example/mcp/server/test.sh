#!/bin/sh
# Usage: ./test.sh [port]   (server must already be running)
URL="http://127.0.0.1:${1:-3000}/mcp"
V='2026-07-28'
META='"_meta":{"io.modelcontextprotocol/protocolVersion":"'$V'","io.modelcontextprotocol/clientInfo":{"name":"curl","version":"1"},"io.modelcontextprotocol/clientCapabilities":{}}'

# post "<title>" "<json body>" [extra curl args...]
post() {
  title="$1"; body="$2"; shift 2
  echo "### $title"
  curl -s -w '\n-> HTTP %{http_code}\n\n' -X POST "$URL" \
    -H 'Content-Type: application/json' \
    -H 'Accept: application/json, text/event-stream' "$@" -d "$body"
}

post "1. server/discover" \
  '{"jsonrpc":"2.0","id":1,"method":"server/discover","params":{'"$META"'}}' \
  -H "MCP-Protocol-Version: $V" -H 'Mcp-Method: server/discover'

post "2. tools/list" \
  '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{'"$META"'}}' \
  -H "MCP-Protocol-Version: $V" -H 'Mcp-Method: tools/list'

post "3. tools/call get_current_time (Europe/Stockholm)" \
  '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_current_time","arguments":{"timezone":"Europe/Stockholm"},'"$META"'}}' \
  -H "MCP-Protocol-Version: $V" -H 'Mcp-Method: tools/call' -H 'Mcp-Name: get_current_time'

post "4. tools/call with a bad time zone (tool execution error, HTTP 200)" \
  '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"get_current_time","arguments":{"timezone":"Mars/Olympus"},'"$META"'}}' \
  -H "MCP-Protocol-Version: $V" -H 'Mcp-Method: tools/call' -H 'Mcp-Name: get_current_time'

post "5. Mcp-Name header does not match body (-32020, HTTP 400)" \
  '{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"get_current_time","arguments":{},'"$META"'}}' \
  -H "MCP-Protocol-Version: $V" -H 'Mcp-Method: tools/call' -H 'Mcp-Name: something_else'

post "6. Missing MCP-Protocol-Version header (-32020, HTTP 400)" \
  '{"jsonrpc":"2.0","id":6,"method":"tools/list","params":{'"$META"'}}' \
  -H 'Mcp-Method: tools/list'

post "7. Unsupported version (-32022, HTTP 400)" \
  '{"jsonrpc":"2.0","id":7,"method":"tools/list","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"1900-01-01","io.modelcontextprotocol/clientCapabilities":{}}}}' \
  -H 'MCP-Protocol-Version: 1900-01-01' -H 'Mcp-Method: tools/list'

post "8. Missing _meta in body (-32602, HTTP 400)" \
  '{"jsonrpc":"2.0","id":8,"method":"tools/list","params":{}}' \
  -H "MCP-Protocol-Version: $V" -H 'Mcp-Method: tools/list'

post "9. Unknown method (-32601, HTTP 404)" \
  '{"jsonrpc":"2.0","id":9,"method":"ping","params":{'"$META"'}}' \
  -H "MCP-Protocol-Version: $V" -H 'Mcp-Method: ping'

post "10. Legacy initialize (names supported versions)" \
  '{"jsonrpc":"2.0","id":10,"method":"initialize","params":{"protocolVersion":"2025-11-25"}}'

post "11. Notification (HTTP 202, empty body)" \
  '{"jsonrpc":"2.0","method":"notifications/whatever"}'

post "12. Bad Origin (HTTP 403)" \
  '{"jsonrpc":"2.0","id":12,"method":"tools/list","params":{'"$META"'}}' \
  -H "MCP-Protocol-Version: $V" -H 'Mcp-Method: tools/list' -H 'Origin: https://evil.example'

echo "### 13. GET (HTTP 405)"
curl -s -o /dev/null -w '-> HTTP %{http_code}\n' "$URL"
