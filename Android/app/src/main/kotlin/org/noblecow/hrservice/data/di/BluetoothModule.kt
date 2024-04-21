package org.noblecow.hrservice.data.di

import android.bluetooth.BluetoothManager
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.annotation.Nullable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(ViewModelComponent::class)
object BluetoothModule {
    @Nullable
    @Provides
    fun provideBluetoothManager(@ApplicationContext context: Context): BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?

    @Provides
    fun provideDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
