package dev.origin.ote.di
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.origin.ote.pack.PackCodec
import dev.origin.ote.root.RootManager
import javax.inject.Singleton
@Module @InstallIn(SingletonComponent::class) object AppModule {
 @Provides @Singleton fun codec(): PackCodec = PackCodec()
 @Provides @Singleton fun root(): RootManager = RootManager()
}
