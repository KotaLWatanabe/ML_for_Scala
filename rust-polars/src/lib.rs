use jni::objects::{JClass, JString, JByteArray};
use jni::sys::{jstring, jint, jlong};
use jni::JNIEnv;
use polars::prelude::*;
use serde_json::json;
use std::collections::HashMap;
use std::io::Cursor;
use std::sync::Mutex;
use std::collections::BTreeMap;

/// Create a sample DataFrame and return as JSON string
#[no_mangle]
pub extern "system" fn Java_com_mlscala_polars_PolarsJNI_createSampleDataFrame(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let result = create_sample_dataframe();
    match result {
        Ok(json_str) => env.new_string(json_str).unwrap().into_raw(),
        Err(e) => env.new_string(format!("Error: {}", e)).unwrap().into_raw(),
    }
}

/// Read CSV file and return as JSON string
#[no_mangle]
pub extern "system" fn Java_com_mlscala_polars_PolarsJNI_readCsv(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
) -> jstring {
    let path_str: String = env.get_string(&path).unwrap().into();
    let result = read_csv_file(&path_str);
    match result {
        Ok(json_str) => env.new_string(json_str).unwrap().into_raw(),
        Err(e) => env.new_string(format!("Error: {}", e)).unwrap().into_raw(),
    }
}

/// Filter DataFrame by column value and return as JSON
#[no_mangle]
pub extern "system" fn Java_com_mlscala_polars_PolarsJNI_filterDataFrame(
    mut env: JNIEnv,
    _class: JClass,
    csv_path: JString,
    column: JString,
    min_value: f64,
) -> jstring {
    let csv_path_str: String = env.get_string(&csv_path).unwrap().into();
    let column_str: String = env.get_string(&column).unwrap().into();
    
    let result = filter_dataframe(&csv_path_str, &column_str, min_value);
    match result {
        Ok(json_str) => env.new_string(json_str).unwrap().into_raw(),
        Err(e) => env.new_string(format!("Error: {}", e)).unwrap().into_raw(),
    }
}

/// Group by column and aggregate
#[no_mangle]
pub extern "system" fn Java_com_mlscala_polars_PolarsJNI_groupByAndSum(
    mut env: JNIEnv,
    _class: JClass,
    csv_path: JString,
    group_col: JString,
    sum_col: JString,
) -> jstring {
    let csv_path_str: String = env.get_string(&csv_path).unwrap().into();
    let group_col_str: String = env.get_string(&group_col).unwrap().into();
    let sum_col_str: String = env.get_string(&sum_col).unwrap().into();
    
    let result = group_by_and_sum(&csv_path_str, &group_col_str, &sum_col_str);
    match result {
        Ok(json_str) => env.new_string(json_str).unwrap().into_raw(),
        Err(e) => env.new_string(format!("Error: {}", e)).unwrap().into_raw(),
    }
}

/// Create a sample DataFrame for testing
fn create_sample_dataframe() -> PolarsResult<String> {
    let df = df! [
        "name" => ["Alice", "Bob", "Charlie", "Diana", "Eve"],
        "age" => [25, 30, 35, 28, 32],
        "salary" => [50000, 60000, 70000, 55000, 65000],
        "department" => ["Engineering", "Sales", "Engineering", "Marketing", "Sales"]
    ]?;
    
    dataframe_to_json(&df)
}

/// Read CSV file into DataFrame
fn read_csv_file(path: &str) -> PolarsResult<String> {
    let df = CsvReader::from_path(path)?
        .finish()?;
    
    dataframe_to_json(&df)
}

/// Filter DataFrame by column value
fn filter_dataframe(csv_path: &str, column: &str, min_value: f64) -> PolarsResult<String> {
    let df = CsvReader::from_path(csv_path)?
        .finish()?;
    
    let filtered = df.lazy()
        .filter(col(column).gt_eq(lit(min_value)))
        .collect()?;
    
    dataframe_to_json(&filtered)
}

/// Group by column and sum another column
fn group_by_and_sum(csv_path: &str, group_col: &str, sum_col: &str) -> PolarsResult<String> {
    let df = CsvReader::from_path(csv_path)?
        .finish()?;
    
    let grouped = df.lazy()
        .group_by([col(group_col)])
        .agg([col(sum_col).sum()])
        .collect()?;
    
    dataframe_to_json(&grouped)
}

/// Convert DataFrame to JSON string
fn dataframe_to_json(df: &DataFrame) -> PolarsResult<String> {
    let mut result = Vec::new();
    
    for row in 0..df.height() {
        let mut row_map = HashMap::new();
        
        for (col_idx, series) in df.get_columns().iter().enumerate() {
            let col_name = df.get_column_names()[col_idx];
            let value = match series.dtype() {
                DataType::Utf8 => {
                    if let Ok(val) = series.utf8() {
                        json!(val.get(row).unwrap_or(""))
                    } else {
                        json!("")
                    }
                }
                DataType::Int64 => {
                    if let Ok(val) = series.i64() {
                        json!(val.get(row).unwrap_or(0))
                    } else {
                        json!(0)
                    }
                }
                DataType::Float64 => {
                    if let Ok(val) = series.f64() {
                        json!(val.get(row).unwrap_or(0.0))
                    } else {
                        json!(0.0)
                    }
                }
                DataType::Int32 => {
                    if let Ok(val) = series.i32() {
                        json!(val.get(row).unwrap_or(0))
                    } else {
                        json!(0)
                    }
                }
                DataType::Float32 => {
                    if let Ok(val) = series.f32() {
                        json!(val.get(row).unwrap_or(0.0))
                    } else {
                        json!(0.0)
                    }
                }
                _ => {
                    // Fallback: convert to string
                    if let Ok(any_val) = series.get(row) {
                        json!(any_val.to_string())
                    } else {
                        json!("null")
                    }
                }
            };
            row_map.insert(col_name, value);
        }
        result.push(row_map);
    }
    
    Ok(serde_json::to_string(&result).unwrap())
}

// === Streaming Processing Functions ===

/// Stream processor session state
static STREAM_PROCESSORS: Mutex<BTreeMap<jlong, StreamProcessor>> = Mutex::new(BTreeMap::new());
static NEXT_PROCESSOR_ID: Mutex<jlong> = Mutex::new(1);

struct StreamProcessor {
    operation: String,
    accumulated_data: Vec<HashMap<String, serde_json::Value>>,
    schema: Option<Vec<String>>,
}

/// Initialize a streaming processor session
#[no_mangle]
pub extern "system" fn Java_com_mlscala_polars_PolarsJNI_initStreamProcessor(
    mut env: JNIEnv,
    _class: JClass,
    operation: JString,
) -> jlong {
    let operation_str: String = env.get_string(&operation).unwrap().into();
    
    let processor = StreamProcessor {
        operation: operation_str,
        accumulated_data: Vec::new(),
        schema: None,
    };
    
    let mut processors = STREAM_PROCESSORS.lock().unwrap();
    let mut next_id = NEXT_PROCESSOR_ID.lock().unwrap();
    let processor_id = *next_id;
    *next_id += 1;
    
    processors.insert(processor_id, processor);
    processor_id
}

/// Process a chunk of CSV data as bytes
#[no_mangle]
pub extern "system" fn Java_com_mlscala_polars_PolarsJNI_processCSVChunk(
    mut env: JNIEnv,
    _class: JClass,
    processor_id: jlong,
    chunk_data: JByteArray,
) -> jstring {
    let result = process_csv_chunk_internal(&mut env, processor_id, chunk_data);
    match result {
        Ok(json_str) => env.new_string(json_str).unwrap().into_raw(),
        Err(e) => env.new_string(format!("Error: {}", e)).unwrap().into_raw(),
    }
}

fn process_csv_chunk_internal(env: &mut JNIEnv, processor_id: jlong, chunk_data: JByteArray) -> PolarsResult<String> {
    // Get chunk data as bytes
    let chunk_bytes = env.convert_byte_array(chunk_data).map_err(|e| 
        PolarsError::ComputeError(format!("Failed to read chunk data: {}", e).into()))?;
    
    let chunk_str = String::from_utf8(chunk_bytes).map_err(|e|
        PolarsError::ComputeError(format!("Invalid UTF-8 in chunk: {}", e).into()))?;
    
    // Parse CSV chunk
    let cursor = Cursor::new(chunk_str.as_bytes());
    let df = CsvReader::new(cursor)
        .finish()?;
    
    // Process according to operation
    let mut processors = STREAM_PROCESSORS.lock().unwrap();
    let processor = processors.get_mut(&processor_id).ok_or_else(||
        PolarsError::ComputeError("Invalid processor ID".into()))?;
    
    // Store schema from first chunk
    if processor.schema.is_none() {
        processor.schema = Some(df.get_column_names().iter().map(|s| s.to_string()).collect());
    }
    
    // Convert to JSON and accumulate
    let chunk_json = dataframe_to_json_vec(&df)?;
    processor.accumulated_data.extend(chunk_json);
    
    // Return progress info
    Ok(format!("{{\"processed_rows\": {}, \"total_accumulated\": {}}}", 
               df.height(), processor.accumulated_data.len()))
}

/// Get final results from stream processor
#[no_mangle]
pub extern "system" fn Java_com_mlscala_polars_PolarsJNI_getStreamResults(
    mut env: JNIEnv,
    _class: JClass,
    processor_id: jlong,
    operation: JString,
) -> jstring {
    let operation_str: String = env.get_string(&operation).unwrap().into();
    let result = get_stream_results_internal(processor_id, &operation_str);
    match result {
        Ok(json_str) => env.new_string(json_str).unwrap().into_raw(),
        Err(e) => env.new_string(format!("Error: {}", e)).unwrap().into_raw(),
    }
}

fn get_stream_results_internal(processor_id: jlong, operation: &str) -> PolarsResult<String> {
    let mut processors = STREAM_PROCESSORS.lock().unwrap();
    let processor = processors.get(&processor_id).ok_or_else(||
        PolarsError::ComputeError("Invalid processor ID".into()))?;
    
    if processor.accumulated_data.is_empty() {
        return Ok("[]".to_string());
    }
    
    // Create DataFrame from accumulated data
    let df = json_vec_to_dataframe(&processor.accumulated_data, &processor.schema)?;
    
    // Apply operation
    let result_df = match operation {
        op if op.starts_with("filter:") => {
            let parts: Vec<&str> = op.split(':').collect();
            if parts.len() >= 3 {
                let column = parts[1];
                let min_value: f64 = parts[2].parse().unwrap_or(0.0);
                df.lazy()
                    .filter(col(column).gt_eq(lit(min_value)))
                    .collect()?
            } else {
                df
            }
        }
        op if op.starts_with("groupby:") => {
            let parts: Vec<&str> = op.split(':').collect();
            if parts.len() >= 3 {
                let group_col = parts[1];
                let sum_col = parts[2];
                df.lazy()
                    .group_by([col(group_col)])
                    .agg([col(sum_col).sum()])
                    .collect()?
            } else {
                df
            }
        }
        _ => df
    };
    
    dataframe_to_json(&result_df)
}

/// Close and cleanup stream processor
#[no_mangle]
pub extern "system" fn Java_com_mlscala_polars_PolarsJNI_closeStreamProcessor(
    _env: JNIEnv,
    _class: JClass,
    processor_id: jlong,
) {
    let mut processors = STREAM_PROCESSORS.lock().unwrap();
    processors.remove(&processor_id);
}

/// Process large CSV file with streaming in Rust
#[no_mangle]
pub extern "system" fn Java_com_mlscala_polars_PolarsJNI_streamProcessCSV(
    mut env: JNIEnv,
    _class: JClass,
    file_path: JString,
    chunk_size: jint,
    operation: JString,
) -> jstring {
    let file_path_str: String = env.get_string(&file_path).unwrap().into();
    let operation_str: String = env.get_string(&operation).unwrap().into();
    
    let result = stream_process_csv(&file_path_str, chunk_size as usize, &operation_str);
    match result {
        Ok(json_str) => env.new_string(json_str).unwrap().into_raw(),
        Err(e) => env.new_string(format!("Error: {}", e)).unwrap().into_raw(),
    }
}

fn stream_process_csv(file_path: &str, _chunk_size: usize, operation: &str) -> PolarsResult<String> {
    // Read CSV file using regular reader for now
    let df = CsvReader::from_path(file_path)?.finish()?;
    let lf = df.lazy();
    
    // Apply operation as lazy computation
    let processed_lf = match operation {
        op if op.starts_with("filter:") => {
            let parts: Vec<&str> = op.split(':').collect();
            if parts.len() >= 3 {
                let column = parts[1];
                let min_value: f64 = parts[2].parse().unwrap_or(0.0);
                lf.filter(col(column).gt_eq(lit(min_value)))
            } else {
                lf
            }
        }
        op if op.starts_with("groupby:") => {
            let parts: Vec<&str> = op.split(':').collect();
            if parts.len() >= 3 {
                let group_col = parts[1];
                let sum_col = parts[2];
                lf.group_by([col(group_col)])
                  .agg([col(sum_col).sum()])
            } else {
                lf
            }
        }
        _ => lf
    };
    
    // Collect with streaming (if result is small enough)
    let result_df = processed_lf.collect()?;
    dataframe_to_json(&result_df)
}

// Utility functions for streaming

fn dataframe_to_json_vec(df: &DataFrame) -> PolarsResult<Vec<HashMap<String, serde_json::Value>>> {
    let mut result = Vec::new();
    
    for row in 0..df.height() {
        let mut row_map = HashMap::new();
        
        for (col_idx, series) in df.get_columns().iter().enumerate() {
            let col_name = df.get_column_names()[col_idx];
            let value = series_value_to_json(series, row)?;
            row_map.insert(col_name.to_string(), value);
        }
        result.push(row_map);
    }
    
    Ok(result)
}

fn json_vec_to_dataframe(data: &[HashMap<String, serde_json::Value>], schema: &Option<Vec<String>>) -> PolarsResult<DataFrame> {
    if data.is_empty() {
        return Ok(DataFrame::empty());
    }
    
    let default_columns: Vec<String> = if let Some(first_row) = data.first() {
        first_row.keys().cloned().collect()
    } else {
        Vec::new()
    };
    let columns = schema.as_ref().unwrap_or(&default_columns);
    let mut series_vec = Vec::new();
    
    for col_name in columns {
        let values: Vec<AnyValue> = data.iter()
            .map(|row| json_value_to_any_value(row.get(col_name).unwrap_or(&serde_json::Value::Null)))
            .collect();
        
        let series = Series::new(col_name, &values);
        series_vec.push(series);
    }
    
    DataFrame::new(series_vec)
}

fn series_value_to_json(series: &Series, row: usize) -> PolarsResult<serde_json::Value> {
    match series.dtype() {
        DataType::Utf8 => {
            if let Ok(val) = series.utf8() {
                Ok(json!(val.get(row).unwrap_or("")))
            } else {
                Ok(json!(""))
            }
        }
        DataType::Int64 => {
            if let Ok(val) = series.i64() {
                Ok(json!(val.get(row).unwrap_or(0)))
            } else {
                Ok(json!(0))
            }
        }
        DataType::Float64 => {
            if let Ok(val) = series.f64() {
                Ok(json!(val.get(row).unwrap_or(0.0)))
            } else {
                Ok(json!(0.0))
            }
        }
        DataType::Int32 => {
            if let Ok(val) = series.i32() {
                Ok(json!(val.get(row).unwrap_or(0)))
            } else {
                Ok(json!(0))
            }
        }
        DataType::Float32 => {
            if let Ok(val) = series.f32() {
                Ok(json!(val.get(row).unwrap_or(0.0)))
            } else {
                Ok(json!(0.0))
            }
        }
        _ => {
            if let Ok(any_val) = series.get(row) {
                Ok(json!(any_val.to_string()))
            } else {
                Ok(json!("null"))
            }
        }
    }
}

fn json_value_to_any_value(value: &serde_json::Value) -> AnyValue {
    match value {
        serde_json::Value::Null => AnyValue::Null,
        serde_json::Value::Bool(b) => AnyValue::Boolean(*b),
        serde_json::Value::Number(n) => {
            if let Some(i) = n.as_i64() {
                AnyValue::Int64(i)
            } else if let Some(f) = n.as_f64() {
                AnyValue::Float64(f)
            } else {
                AnyValue::Null
            }
        }
        serde_json::Value::String(s) => AnyValue::Utf8(s.as_str()),
        _ => AnyValue::Utf8("null"),
    }
}
