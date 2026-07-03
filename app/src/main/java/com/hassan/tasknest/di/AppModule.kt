package com.hassan.tasknest.di

import com.hassan.tasknest.data.datastore.UserPreferencesDataStore
import com.hassan.tasknest.data.repository.TaskRepository
import com.hassan.tasknest.data.local.AppDatabase
import com.hassan.tasknest.data.local.dao.CategoryDao
import com.hassan.tasknest.data.local.dao.TaskDao
import com.hassan.tasknest.presentation.tasklist.TaskListViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<AppDatabase> { AppDatabase.getInstance(androidContext()) }
    single<TaskDao> { get<AppDatabase>().taskDao() }
    single<CategoryDao> { get<AppDatabase>().categoryDao() }
    single { UserPreferencesDataStore(androidContext()) }
    single { TaskRepository(get()) }
    viewModel { TaskListViewModel(get()) }
}
