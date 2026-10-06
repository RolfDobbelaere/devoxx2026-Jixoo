#!/usr/bin/env bash
# Re-encodes a GIF for the raffle's 1 MB upload limit without touching the frame rate.
#
# Jixoo's GifEncoder writes a full 256-color palette and every pixel for every frame. Here ffmpeg
#   1. builds ONE shared 256-color palette for the whole animation (saves ~770 bytes per frame),
#   2. maps colors without dithering (dither noise compresses badly and flickers on LEDs),
#   3. stores only the rectangle that changed since the previous frame, with transparency for
#      unchanged pixels (transdiff + offsetting).
#
# Usage: scripts/gif-1mb.sh input.gif output.gif [max_colors]
set -euo pipefail
in="$1"; out="$2"; colors="${3:-256}"
ffmpeg -v error -y -i "$in" \
  -vf "split[a][b];[a]palettegen=max_colors=${colors}:stats_mode=full:reserve_transparent=1[p];[b][p]paletteuse=dither=none:diff_mode=rectangle" \
  -gifflags +transdiff+offsetting "$out"
size=$(stat -c %s "$out" 2>/dev/null || stat -f %z "$out")
echo "$out: $size bytes ($(awk "BEGIN{printf \"%.0f\", $size/1000}") KB)"
if [ "$size" -gt 1000000 ]; then echo "WARNING: over 1 MB"; exit 1; fi
