#!/bin/bash

# Build script for Rust Polars library with JNI
set -e

echo "🦀 Building Rust Polars library for JNI..."
echo "================================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check if Rust is installed
check_rust() {
    echo -e "${BLUE}Checking Rust installation...${NC}"
    if ! command -v cargo &> /dev/null; then
        echo -e "${RED}❌ Rust/Cargo not found. Please install Rust first.${NC}"
        echo "Installation: https://rustup.rs/"
        exit 1
    fi
    echo -e "${GREEN}✅ Rust found${NC}"
    cargo --version
}

# Check Java/JDK for JNI headers
check_java() {
    echo -e "${BLUE}Checking Java/JDK installation...${NC}"
    if [ -z "$JAVA_HOME" ]; then
        echo -e "${YELLOW}⚠️  JAVA_HOME not set. Trying to detect...${NC}"
        # Try to find Java installation
        if command -v java &> /dev/null; then
            JAVA_PATH=$(which java)
            JAVA_HOME=$(dirname $(dirname $(readlink -f $JAVA_PATH)))
            export JAVA_HOME
            echo -e "${GREEN}✅ Found Java at: $JAVA_HOME${NC}"
        else
            echo -e "${RED}❌ Java not found. Please install JDK and set JAVA_HOME.${NC}"
            exit 1
        fi
    else
        echo -e "${GREEN}✅ JAVA_HOME set to: $JAVA_HOME${NC}"
    fi
}

# Build the Rust library
build_library() {
    echo -e "${BLUE}Building Rust library...${NC}"
    
    # Build in release mode for better performance
    cargo build --release
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ Rust library built successfully!${NC}"
        
        # Check if the library was created
        if [ -f "target/release/librust_polars.so" ]; then
            echo -e "${GREEN}📦 Library location: target/release/librust_polars.so${NC}"
            echo -e "${BLUE}Library size: $(du -h target/release/librust_polars.so | cut -f1)${NC}"
        elif [ -f "target/release/librust_polars.dylib" ]; then
            echo -e "${GREEN}📦 Library location: target/release/librust_polars.dylib${NC}"
            echo -e "${BLUE}Library size: $(du -h target/release/librust_polars.dylib | cut -f1)${NC}"
        else
            echo -e "${YELLOW}⚠️  Library file not found in expected location${NC}"
        fi
    else
        echo -e "${RED}❌ Rust library build failed${NC}"
        exit 1
    fi
}

# Copy library to resources
copy_to_resources() {
    echo -e "${BLUE}Copying library to output directories...${NC}"
    
    # Create output directories
    mkdir -p ../out/rust/release
    mkdir -p ../out/resources/native
    
    # Copy the library file to both rust output and resources
    if [ -f "target/release/librust_polars.so" ]; then
        cp target/release/librust_polars.so ../out/rust/release/
        cp target/release/librust_polars.so ../out/resources/native/
        echo -e "${GREEN}✅ Copied librust_polars.so to out directories${NC}"
    elif [ -f "target/release/librust_polars.dylib" ]; then
        cp target/release/librust_polars.dylib ../out/rust/release/
        cp target/release/librust_polars.dylib ../out/resources/native/
        echo -e "${GREEN}✅ Copied librust_polars.dylib to out directories${NC}"
    else
        echo -e "${YELLOW}⚠️  No library file found to copy${NC}"
    fi
    
    # Also maintain compatibility with old location
    mkdir -p ../src/main/resources/native
    if [ -f "target/release/librust_polars.so" ]; then
        cp target/release/librust_polars.so ../src/main/resources/native/
    elif [ -f "target/release/librust_polars.dylib" ]; then
        cp target/release/librust_polars.dylib ../src/main/resources/native/
    fi
}

# Generate JNI headers (optional)
generate_headers() {
    echo -e "${BLUE}Generating JNI headers...${NC}"
    
    cd ..
    if [ -d "out/scala/compile/classes" ]; then
        mkdir -p out/rust/include
        javah -d out/rust/include -classpath out/scala/compile/classes com.mlscala.polars.PolarsJNI 2>/dev/null || true
        echo -e "${GREEN}✅ JNI headers generated (if applicable)${NC}"
    elif [ -d "target/scala-3.7.1/classes" ]; then
        mkdir -p out/rust/include
        javah -d out/rust/include -classpath target/scala-3.7.1/classes com.mlscala.polars.PolarsJNI 2>/dev/null || true
        echo -e "${GREEN}✅ JNI headers generated (if applicable)${NC}"
    else
        echo -e "${YELLOW}⚠️  Scala classes not found. Compile Scala project first.${NC}"
    fi
    cd rust-polars
}

# Show usage information
show_usage() {
    echo -e "${GREEN}🎉 Build completed successfully!${NC}"
    echo ""
    echo -e "${YELLOW}Next steps:${NC}"
    echo "1. Compile the Scala project: cd .. && sbt compile"
    echo "2. Run the application: sbt run"
    echo "3. Build native image: sbt nativeImage"
    echo ""
    echo -e "${YELLOW}Output files:${NC}"
    echo "  Rust library: ./target/release/librust_polars.{so,dylib}"
    echo "  Output copy: ../out/rust/release/librust_polars.{so,dylib}"
    echo "  Resources: ../out/resources/native/"
    echo "  Headers: ../out/rust/include/"
    echo ""
    echo -e "${YELLOW}For development:${NC}"
    echo "  cargo test          # Run Rust tests"
    echo "  cargo doc --open    # Generate and open documentation"
}

# Main execution
main() {
    echo -e "${BLUE}Starting Rust Polars build process...${NC}"
    echo ""
    
    check_rust
    check_java
    echo ""
    
    build_library
    echo ""
    
    copy_to_resources
    echo ""
    
    generate_headers
    echo ""
    
    show_usage
}

# Handle command line arguments
case "${1:-}" in
    --help|-h)
        echo "Rust Polars JNI Build Script"
        echo ""
        echo "Usage: $0 [OPTIONS]"
        echo ""
        echo "Options:"
        echo "  --help, -h     Show this help message"
        echo "  --clean        Clean build artifacts"
        echo "  --dev          Build in development mode"
        echo ""
        exit 0
        ;;
    --clean)
        echo "Cleaning build artifacts..."
        cargo clean
        rm -rf ../out/rust/release/librust_polars.*
        rm -rf ../out/resources/native/librust_polars.*
        rm -rf ../src/main/resources/native/librust_polars.*
        echo -e "${GREEN}✅ Clean completed.${NC}"
        exit 0
        ;;
    --dev)
        echo "Building in development mode..."
        build_library() {
            echo -e "${BLUE}Building Rust library (debug mode)...${NC}"
            cargo build
            echo -e "${GREEN}✅ Debug build completed${NC}"
        }
        ;;
esac

# Run main function
main