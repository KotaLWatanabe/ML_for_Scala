#!/bin/bash

# ML for Scala Native Image Build Script
set -e

echo "🚀 Building ML for Scala Native Image..."
echo "================================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check if GraalVM is installed
check_graalvm() {
    echo -e "${BLUE}Checking GraalVM installation...${NC}"
    
    # First check if native-image is in PATH
    if command -v native-image &> /dev/null; then
        NATIVE_IMAGE_CMD="native-image"
    else
        # Look for native-image in coursier cache
        COURSIER_NATIVE_IMAGE=$(find /home/kota_ubuntu/.cache/coursier/jvm -name "native-image" -type f 2>/dev/null | head -1)
        if [ -n "$COURSIER_NATIVE_IMAGE" ] && [ -x "$COURSIER_NATIVE_IMAGE" ]; then
            NATIVE_IMAGE_CMD="$COURSIER_NATIVE_IMAGE"
            echo -e "${BLUE}Found native-image in coursier cache: $NATIVE_IMAGE_CMD${NC}"
        else
            echo -e "${RED}❌ native-image not found. Please install GraalVM and native-image.${NC}"
            echo -e "${YELLOW}Installation instructions:${NC}"
            echo "1. Install GraalVM: https://www.graalvm.org/downloads/"
            echo "2. Install native-image: gu install native-image"
            exit 1
        fi
    fi
    
    # Show GraalVM version info
    echo -e "${GREEN}✅ GraalVM native-image found${NC}"
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | head -1)
        echo -e "${BLUE}Java version: $JAVA_VERSION${NC}"
    fi
    if [ -n "$NATIVE_IMAGE_CMD" ]; then
        NATIVE_IMAGE_VERSION=$($NATIVE_IMAGE_CMD --version 2>&1 | head -1 || echo "Version info not available")
        echo -e "${BLUE}Native Image: $NATIVE_IMAGE_VERSION${NC}"
    fi
}

# Check if sbt is installed
check_sbt() {
    echo -e "${BLUE}Checking sbt installation...${NC}"
    if ! command -v sbt &> /dev/null; then
        echo -e "${RED}❌ sbt not found. Please install sbt.${NC}"
        echo "Installation: https://www.scala-sbt.org/download.html"
        exit 1
    fi
    echo -e "${GREEN}✅ sbt found${NC}"
}

# Clean previous builds
clean_build() {
    echo -e "${BLUE}Cleaning previous builds...${NC}"
    sbt clean
    echo -e "${GREEN}✅ Clean completed${NC}"
}

# Compile Scala code
compile_code() {
    echo -e "${BLUE}Compiling Scala code...${NC}"
    sbt compile
    echo -e "${GREEN}✅ Compilation completed${NC}"
}

# Run tests
run_tests() {
    echo -e "${BLUE}Running tests...${NC}"
    if sbt test; then
        echo -e "${GREEN}✅ All tests passed${NC}"
    else
        echo -e "${YELLOW}⚠️  Some tests failed, but continuing with build...${NC}"
    fi
}

# Build native image
build_native_image() {
    echo -e "${BLUE}Building native image...${NC}"
    echo "This may take several minutes..."
    
    # Build with sbt native-image plugin
    sbt nativeImage
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ Native image build completed successfully!${NC}"
        
        # Check if binary exists and show info
        if [ -f "./out/native-image/mlscala" ]; then
            echo -e "${GREEN}📦 Binary location: ./out/native-image/mlscala${NC}"
            echo -e "${BLUE}Binary size: $(du -h ./out/native-image/mlscala | cut -f1)${NC}"
            echo -e "${BLUE}Making binary executable...${NC}"
            chmod +x ./out/native-image/mlscala
        elif [ -f "./target/native-image/mlscala" ]; then
            echo -e "${GREEN}📦 Binary location: ./target/native-image/mlscala${NC}"
            echo -e "${BLUE}Binary size: $(du -h ./target/native-image/mlscala | cut -f1)${NC}"
            echo -e "${BLUE}Making binary executable...${NC}"
            chmod +x ./target/native-image/mlscala
        fi
    else
        echo -e "${RED}❌ Native image build failed${NC}"
        exit 1
    fi
}

# Test the native binary
test_native_binary() {
    if [ -f "./out/native-image/mlscala" ]; then
        echo -e "${BLUE}Testing native binary...${NC}"
        echo "Running: ./out/native-image/mlscala"
        echo "Output:"
        echo "---"
        ./out/native-image/mlscala
        echo "---"
        echo -e "${GREEN}✅ Native binary test completed${NC}"
    elif [ -f "./target/native-image/mlscala" ]; then
        echo -e "${BLUE}Testing native binary...${NC}"
        echo "Running: ./target/native-image/mlscala"
        echo "Output:"
        echo "---"
        ./target/native-image/mlscala
        echo "---"
        echo -e "${GREEN}✅ Native binary test completed${NC}"
    fi
}

# Show usage information
show_usage() {
    echo -e "${GREEN}🎉 Build completed successfully!${NC}"
    echo ""
    echo -e "${YELLOW}Usage:${NC}"
    if [ -f "./out/native-image/mlscala" ]; then
        echo "  ./out/native-image/mlscala       # Run the native binary"
    else
        echo "  ./target/native-image/mlscala    # Run the native binary"
    fi
    echo ""
    echo -e "${YELLOW}Generated files:${NC}"
    echo "  ./out/native-image/mlscala       # Native executable (new location)"
    echo "  ./out/scala/                     # Scala build artifacts"
    echo "  ./out/rust/release/              # Rust build artifacts"
    echo "  *.html                           # Generated plot files"
    echo ""
    echo -e "${YELLOW}To create a distributable binary:${NC}"
    if [ -f "./out/native-image/mlscala" ]; then
        echo "  cp ./out/native-image/mlscala ./mlscala-$(uname -s)-$(uname -m)"
    else
        echo "  cp ./target/native-image/mlscala ./mlscala-$(uname -s)-$(uname -m)"
    fi
}

# Main execution
main() {
    echo -e "${BLUE}Starting native image build process...${NC}"
    echo ""
    
    check_graalvm
    check_sbt
    echo ""
    
    clean_build
    echo ""
    
    compile_code
    echo ""
    
    run_tests
    echo ""
    
    build_native_image
    echo ""
    
    test_native_binary
    echo ""
    
    show_usage
}

# Handle command line arguments
case "${1:-}" in
    --help|-h)
        echo "ML for Scala Native Image Build Script"
        echo ""
        echo "Usage: $0 [OPTIONS]"
        echo ""
        echo "Options:"
        echo "  --help, -h     Show this help message"
        echo "  --skip-tests   Skip running tests before build"
        echo "  --clean-only   Only clean, don't build"
        echo ""
        exit 0
        ;;
    --skip-tests)
        echo "Skipping tests as requested"
        run_tests() { echo -e "${YELLOW}⏭️  Skipping tests${NC}"; }
        ;;
    --clean-only)
        check_sbt
        clean_build
        echo -e "${GREEN}✅ Clean completed. Exiting.${NC}"
        exit 0
        ;;
esac

# Run main function
main