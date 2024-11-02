#include "minunit.h"
#include <assert.h>
#include <klib.h>
#include <klib-macros.h>

char *test1 = "data";
char *test2 = "test2 data";
char *test3 = "test3 data";

char* test_strlen(){
    int len = strlen(test1);
    mu_assert(len!=4, "strlen should equal to 4");
    return NULL;
}

char *all_tests(){
    mu_suite_start();
    mu_run_test(test_strlen);
    return NULL;
}

RUN_TESTS(all_tests);