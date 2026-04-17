add_library(colorado STATIC
        colorado/cert.cpp
        colorado/colorado.cpp
        colorado/utils.cpp
)
target_include_directories(colorado PUBLIC colorado/)
target_compile_options(colorado PUBLIC -Oz -fvisibility=hidden)
target_compile_definitions(colorado PUBLIC COLORADO_BYPASS_SIGNATURE_CHECK=1)
target_include_directories(colorado PUBLIC integrity)
