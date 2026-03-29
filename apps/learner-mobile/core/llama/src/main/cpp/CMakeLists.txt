cmake_minimum_required(VERSION 3.22.1)
project(llama-jni C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Path to vendored llama.cpp source (relative to this CMakeLists.txt)
# From core/llama/src/main/cpp/ → 5 levels up to apps/learner-mobile/
set(LLAMA_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../third-party/llama.cpp")

# Build llama.cpp as a subdirectory
add_subdirectory(${LLAMA_CPP_DIR} ${CMAKE_CURRENT_BINARY_DIR}/llama.cpp)

# Our JNI bridge library
add_library(llama-jni SHARED llama_jni.cpp)

target_include_directories(llama-jni PRIVATE
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/common
)

target_link_libraries(llama-jni
    llama
    common
    log
)
