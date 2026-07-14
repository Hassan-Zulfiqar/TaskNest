package com.hassan.tasknest.di

import com.hassan.tasknest.data.datastore.UserPreferencesDataStore
import com.hassan.tasknest.data.repository.CategoryRepository
import com.hassan.tasknest.data.repository.NoteRepository
import com.hassan.tasknest.data.repository.TaskRepository
import com.hassan.tasknest.data.local.AppDatabase
import com.hassan.tasknest.data.local.dao.CategoryDao
import com.hassan.tasknest.data.local.dao.NoteDao
import com.hassan.tasknest.data.local.dao.TaskDao
import com.hassan.tasknest.data.repository.PreferencesRepository
import com.hassan.tasknest.presentation.addedittask.AddEditTaskViewModel
import com.hassan.tasknest.presentation.addeditnotes.AddEditNoteViewModel
import com.hassan.tasknest.presentation.category.CategoryViewModel
import com.hassan.tasknest.presentation.noteslist.NotesListViewModel
import com.hassan.tasknest.presentation.settings.SettingsViewModel
import com.hassan.tasknest.presentation.taskdetail.TaskDetailViewModel
import com.hassan.tasknest.presentation.tasklist.TaskListViewModel
import com.hassan.tasknest.voice.VoskModelManager
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<AppDatabase> { AppDatabase.getInstance(androidContext()) }
    single<TaskDao> { get<AppDatabase>().taskDao() }
    single<CategoryDao> { get<AppDatabase>().categoryDao() }
    single<NoteDao> { get<AppDatabase>().noteDao() }
    single { UserPreferencesDataStore(androidContext()) }
    single { TaskRepository(get(), androidContext()) }
    single { CategoryRepository(get()) }
    single { NoteRepository(get()) }
    single { PreferencesRepository(get()) }
    viewModel { TaskListViewModel(get<TaskRepository>(), get<PreferencesRepository>()) }
    viewModel { AddEditTaskViewModel(get<TaskRepository>(), get<CategoryRepository>()) }
    viewModel { AddEditNoteViewModel(get()) }
    viewModel { TaskDetailViewModel(get()) }
    viewModel { CategoryViewModel(get<CategoryRepository>(), get<TaskRepository>()) }
    viewModel { NotesListViewModel(get()) }
    viewModel { SettingsViewModel(get(), androidContext()) }
    single { VoskModelManager(androidContext()) }
}
