#include "minunit.h"
#include <lcthw/list.h>
#include <assert.h>
#include <klib.h>
#include <klib-macros.h>

static List *list = NULL;
char *test1 = "test1 data";
char *test2 = "test2 data";
char *test3 = "test3 data";

char* test_create(){
    list = List_create();
    mu_assert(list!=NULL, "Failed to create list");
    return NULL;
}

char* test_destroy(){
    List_destroy(list);
    return NULL;
}

char *test_push_pop(){
    List_push(list,test1);
    mu_assert(List_last(list)==test1,"test1 push failed");
    List_push(list,test2);
    mu_assert(List_last(list)==test2,"test2 push failed");
    List_push(list,test3);
    mu_assert(List_last(list)==test3,"test3 push failed");
    mu_assert(List_count(list)==3,"count 3 push failed");
    List_pop(list);
    mu_assert(List_last(list)==test2,"test3 pop failed");
    List_pop(list);
    mu_assert(List_last(list)==test1,"test1 pop failed");
    List_pop(list);
    mu_assert(List_count(list)==0,"count 0 push failed");
    return NULL;
}

char *test_unshift_shift(){
    List_unshift(list,test1);
    mu_assert(List_first(list)==test1,"test1 unshift failed");
    List_unshift(list,test2);
    mu_assert(List_first(list)==test2,"test2 unshift failed");
    List_unshift(list,test3);
    mu_assert(List_first(list)==test3,"test3 unshift failed");
    return NULL;
}

char *test_remove(){
    char* val = List_remove(list,list->first->next);
    mu_assert(val==test2,"remove test2 failed");
    mu_assert(List_first(list)==test3,"remove first test1 failed");
    mu_assert(List_last(list)==test1,"remove last should be test3 failed");
    return NULL;
}

char *test_shift(){
    List_shift(list);
    mu_assert(List_first(list)==test3,"test3 shift failed");
    mu_assert(List_count(list)==2,"shift count 2 failed");
    return NULL;
}

char *all_tests(){
    mu_suite_start();
    mu_run_test(test_create);
    mu_run_test(test_push_pop);
    mu_run_test(test_unshift_shift);
    mu_run_test(test_remove);
    mu_run_test(test_destroy);
    return NULL;
}

RUN_TESTS(all_tests);