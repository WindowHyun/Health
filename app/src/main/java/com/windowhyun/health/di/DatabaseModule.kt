package com.windowhyun.health.di

import android.content.Context
import androidx.room.Room
import com.windowhyun.health.data.local.HealthDatabase
import com.windowhyun.health.data.local.dao.ExerciseDao
import com.windowhyun.health.data.local.dao.PersonalRecordDao
import com.windowhyun.health.data.local.dao.RoutineDao
import com.windowhyun.health.data.local.dao.RunDao
import com.windowhyun.health.data.local.dao.WorkoutDao
import com.windowhyun.health.data.seed.ExerciseSeedCallback
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HealthDatabase =
        Room.databaseBuilder(context, HealthDatabase::class.java, HealthDatabase.NAME)
            .addCallback(ExerciseSeedCallback)
            .addMigrations(*HealthDatabase.MIGRATIONS)
            .build()

    @Provides
    fun provideExerciseDao(db: HealthDatabase): ExerciseDao = db.exerciseDao()

    @Provides
    fun provideRoutineDao(db: HealthDatabase): RoutineDao = db.routineDao()

    @Provides
    fun provideWorkoutDao(db: HealthDatabase): WorkoutDao = db.workoutDao()

    @Provides
    fun provideRunDao(db: HealthDatabase): RunDao = db.runDao()

    @Provides
    fun providePersonalRecordDao(db: HealthDatabase): PersonalRecordDao = db.personalRecordDao()
}
