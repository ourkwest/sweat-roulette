#!/bin/bash

# Watch for CSS changes and copy to .dev-build
echo "Watching src/public/css/styles.css for changes..."

# Use inotifywait if available (Linux), otherwise fall back to a polling loop
if command -v inotifywait &> /dev/null; then
    while inotifywait -e modify src/public/css/styles.css; do
        echo "CSS changed, copying to .dev-build..."
        mkdir -p .dev-build/css
        cp src/public/css/styles.css .dev-build/css/styles.css
        echo "✓ CSS copied"
    done
else
    # Fallback: polling method
    LAST_MODIFIED=$(stat -c %Y src/public/css/styles.css 2>/dev/null || stat -f %m src/public/css/styles.css)
    
    while true; do
        sleep 1
        CURRENT_MODIFIED=$(stat -c %Y src/public/css/styles.css 2>/dev/null || stat -f %m src/public/css/styles.css)
        
        if [ "$CURRENT_MODIFIED" != "$LAST_MODIFIED" ]; then
            echo "CSS changed, copying to .dev-build..."
            mkdir -p .dev-build/css
            cp src/public/css/styles.css .dev-build/css/styles.css
            echo "✓ CSS copied"
            LAST_MODIFIED=$CURRENT_MODIFIED
        fi
    done
fi
