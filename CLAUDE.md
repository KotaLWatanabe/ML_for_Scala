# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Scala 3 machine learning project (`mlscala`) that integrates Scala with Rust Polars for high-performance data processing. The project is configured with sbt and GraalVM native image compilation, featuring a modern functional programming stack with Cats Effect, HTTP4s, Tapir, and native Plotly visualization.

## Development Commands

### Building and Running
- `sbt compile` - Compile the project
- `sbt run` - Run the main application (includes Polars + Plotly demos)
- `sbt nativeImage` - Build GraalVM native image executable

### Testing
- `sbt test` - Run all tests
- `sbt testOnly <TestClassName>` - Run specific test class

### Project Management
- `sbt clean` - Clean build artifacts
- `sbt reload` - Reload sbt configuration after build file changes

### Native Image Build Scripts
- `./build-native.sh` - Unix/Linux native image build script with full automation
- `./build-native.bat` - Windows native image build script with full automation
- `./build-native.sh --help` - Show build script options
- `./build-native.sh --skip-tests` - Build without running tests
- `./build-native.sh --clean-only` - Clean build artifacts only

### Native Image Support
- `sbt nativeImage` - Build GraalVM native image with JNI support
- Configured for GraalVM 22.1.0 with Cats Effect 3.6.1 compatibility
- JNI integration enabled for Rust Polars library
- Native image includes embedded Rust library resources

### Rust Polars Integration
- `cd rust-polars && ./build.sh` - Build the Rust Polars JNI library
- Rust library must be built before running Scala application
- Build artifacts are output to `out/rust/release/` and `out/resources/native/`

## Architecture

### Build Configuration
- **Scala Version**: 3.7.1
- **SBT Version**: 1.9.9
- **Native Image**: GraalVM with static linking and no JVM fallback
- **Dependencies**: Centralized in `project/Libraries.scala` with version management

### Key Libraries
- **Cats Effect**: Functional effect system for IO and concurrency
- **HTTP4s**: HTTP client/server with Ember backend
- **Tapir**: Type-safe API endpoints with OpenAPI generation
- **Pekko**: Actor system (Apache Pekko)
- **Circe**: JSON encoding/decoding for data interchange
- **Iron/Refined**: Type refinement for domain modeling
- **ScalaTest/MUnit**: Testing frameworks with Cats Effect integration

### Multi-Language Architecture
The project demonstrates polyglot programming with:
- **Scala**: Main application logic, functional programming patterns
- **Rust**: High-performance data processing via Polars library
- **JNI Bridge**: Type-safe integration between Scala and Rust via `PolarsJNI`

### Project Structure
- `src/main/scala/com/mlscala/` - Main Scala application code
  - `Main.scala` - Application entry point with demo workflows
  - `plotting/` - Plotly visualization system (PlotlyData, PlotlyService, PlotlyRenderer)
  - `polars/` - Rust Polars integration (DataFrameOps, PolarsJNI)
- `rust-polars/` - Rust crate for Polars data processing
  - `src/lib.rs` - JNI interface implementation
  - `Cargo.toml` - Rust dependencies and build configuration
- `out/` - Build output directory
  - `scala/` - Scala compilation artifacts
  - `rust/release/` - Rust library build artifacts
  - `native-image/` - GraalVM native image executable
  - `resources/native/` - Native library resources
- `project/Libraries.scala` - Centralized dependency management
- `build.sbt` - Main build configuration with native image settings

### Native Image Configuration
GraalVM native image is configured for production deployment:
- Static linking for standalone executables
- No JVM fallback for smaller binaries
- Incomplete classpath handling for third-party libraries
- Exception stack traces enabled for debugging

## Development Notes

### Adding Dependencies
Add new libraries to `project/Libraries.scala` following the existing pattern of version centralization, then reference them in the `common` sequence or create new dependency groups as needed.

### Testing Strategy
The project includes both ScalaTest and MUnit testing frameworks. Use MUnit with Cats Effect integration (`munit-cats-effect-3`) for IO-based tests and asynchronous operations.

### Rust-Scala Integration
The project uses JNI for Scala-Rust interop:
- Build the Rust library first: `cd rust-polars && ./build.sh`
- Rust functions are exposed via `PolarsJNI` object with error-safe wrappers
- All Rust operations return `Either[String, T]` for functional error handling
- DataFrame results are serialized as JSON for type-safe parsing in Scala

### Plotly Graph Generation
Complete Plotly-based visualization system:
- **PlotlyData**: Type-safe data models with Circe JSON encoding
- **PlotlyRenderer**: HTML template generation and file output
- **PlotlyService**: High-level graph creation API (scatter, line, bar, multi-series)
- **PlotDataGenerator**: Utility functions for generating sample data

All plotting functionality is implemented with Cats Effect IO for composability and native image compatibility. Generated plots are saved as standalone HTML files.

## Memories
- To memorize