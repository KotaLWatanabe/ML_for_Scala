use jni::objects::{JClass, JString};
use jni::sys::{jstring};
use jni::JNIEnv;
use polars::prelude::*;
use serde_json::json;
use std::collections::HashMap;

/// Create a sample DataFrame and return as JSON string
#[no_mangle]
pub extern "system" fn Java_com_mlscala_polars_PolarsJNI_createSampleDataFrame(
    mut env: JNIEnv,
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
