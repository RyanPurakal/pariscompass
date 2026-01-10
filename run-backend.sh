#!/bin/bash

# Run Backend Script for Paris Compass
# Make sure GEMINI_API_KEY is set before running

if [ -z "$GEMINI_API_KEY" ]; then
    echo "⚠️  Warning: GEMINI_API_KEY environment variable is not set!"
    echo "Please set it first:"
    echo "  export GEMINI_API_KEY='your_key_here'"
    echo ""
    read -p "Continue anyway? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

echo "🚀 Starting Paris Compass Backend..."
echo "📍 Backend will be available at http://localhost:8081"
echo ""

./mvnw spring-boot:run

