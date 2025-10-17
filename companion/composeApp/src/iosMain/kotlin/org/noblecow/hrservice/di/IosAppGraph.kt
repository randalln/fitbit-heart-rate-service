import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import org.noblecow.hrservice.di.AppGraph

@DependencyGraph(AppScope::class)
internal interface IosAppGraph : AppGraph
