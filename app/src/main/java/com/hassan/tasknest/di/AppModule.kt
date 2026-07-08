package com.hassan.tasknest.di

import com.hassan.tasknest.data.datastore.UserPreferencesDataStore
import com.hassan.tasknest.data.repository.CategoryRepository
import com.hassan.tasknest.data.repository.TaskRepository
import com.hassan.tasknest.data.local.AppDatabase
import com.hassan.tasknest.data.local.dao.CategoryDao
import com.hassan.tasknest.data.local.dao.TaskDao
import com.hassan.tasknest.data.repository.PreferencesRepository
import com.hassan.tasknest.presentation.addedittask.AddEditTaskViewModel
import com.hassan.tasknest.presentation.category.CategoryViewModel
import com.hassan.tasknest.presentation.settings.SettingsViewModel
import com.hassan.tasknest.presentation.taskdetail.TaskDetailViewModel
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
    single { CategoryRepository(get()) }
    single { PreferencesRepository(get()) }
    viewModel { TaskListViewModel(get<TaskRepository>(), get<PreferencesRepository>()) }
    viewModel { AddEditTaskViewModel(get<TaskRepository>(), get<CategoryRepository>()) }
    viewModel { TaskDetailViewModel(get()) }
    viewModel { CategoryViewModel(get<CategoryRepository>(), get<TaskRepository>()) }
    viewModel { SettingsViewModel(get(), androidContext()) }
}
