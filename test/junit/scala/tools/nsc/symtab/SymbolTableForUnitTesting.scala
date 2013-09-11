package scala.tools.nsc
package symtab

import scala.reflect.ClassTag
import scala.reflect.internal.{Phase, NoPhase, SomePhase}
import scala.tools.util.PathResolver
import util.ClassPath
import io.AbstractFile
import scala.tools.nsc.classpath.FlatClasspath
import scala.tools.nsc.classpath.DefaultFlatClasspathManager

/**
 * A complete SymbolTable implementation designed to be used in JUnit tests.
 *
 * It enables `usejavacp` setting so classpath of JUnit runner is being used
 * for symbol table's classpath.
 *
 * This class contains enough of logic implemented to make it possible to
 * initialize definitions and inspect symbols.
 */
class SymbolTableForUnitTesting extends SymbolTable {
  // Members declared in scala.reflect.api.Trees
  override def newStrictTreeCopier: TreeCopier = new StrictTreeCopier
  override def newLazyTreeCopier: TreeCopier = new LazyTreeCopier
  trait TreeCopier extends InternalTreeCopierOps
  // these should be mocks
  class StrictTreeCopier extends super.StrictTreeCopier with TreeCopier
  class LazyTreeCopier extends super.LazyTreeCopier with TreeCopier

  override def isCompilerUniverse: Boolean = true
  def classPath = platform.classPath
  def flatClasspath: FlatClasspath = DefaultFlatClasspathManager.createClasspath(settings)

  object platform extends backend.Platform {
    val symbolTable: SymbolTableForUnitTesting.this.type = SymbolTableForUnitTesting.this
    lazy val loaders: SymbolTableForUnitTesting.this.loaders.type = SymbolTableForUnitTesting.this.loaders
    def platformPhases: List[SubComponent] = Nil
    def classPath: ClassPath[AbstractFile] = {
      assert(settings.YclasspathImpl.value == "recursive")
      new PathResolver(settings).result
    }
    def flatClasspath: FlatClasspath = {
      assert(settings.YclasspathImpl.value == "flat")
      SymbolTableForUnitTesting.this.flatClasspath
    }
    def isMaybeBoxed(sym: Symbol): Boolean = ???
    def needCompile(bin: AbstractFile, src: AbstractFile): Boolean = ???
    def externalEquals: Symbol = ???
    def updateClassPath(subst: Map[ClassPath[AbstractFile], ClassPath[AbstractFile]]): Unit = ???
  }

  object loaders extends symtab.SymbolLoaders {
    val symbolTable: SymbolTableForUnitTesting.this.type = SymbolTableForUnitTesting.this
    lazy val platform: symbolTable.platform.type = symbolTable.platform
    def lookupMemberAtTyperPhaseIfPossible(sym: Symbol, name: Name): Symbol =
      sym.info.member(name)
    protected override def compileLate(srcfile: AbstractFile): Unit =
      sys.error(s"We do not expect compileLate to be called in SymbolTableTest. The srcfile passed in is $srcfile")
  }

  class GlobalMirror extends Roots(NoSymbol) {
    val universe: SymbolTableForUnitTesting.this.type = SymbolTableForUnitTesting.this
    def rootLoader: LazyType = settings.YclasspathImpl.value match {
      case "flat" => new loaders.PackageLoaderUsingFlatClasspath(FlatClasspath.RootPackage, flatClasspath)
      case "recursive" => new loaders.PackageLoader(classPath)
    }
    override def toString = "compiler mirror"
  }

  lazy val rootMirror: Mirror = {
    val rm = new GlobalMirror
    rm.init()
    rm.asInstanceOf[Mirror]
  }

  lazy val settings: Settings = {
    val s = new Settings
    // initialize classpath using java classpath
    s.usejavacp.value = true
    s
  }

   // Members declared in scala.reflect.internal.Required
  def picklerPhase: scala.reflect.internal.Phase = SomePhase
  def erasurePhase: scala.reflect.internal.Phase = SomePhase

  // Members declared in scala.reflect.internal.SymbolTable
  def currentRunId: Int = 1
  def log(msg: => AnyRef): Unit = println(msg)
  def mirrorThatLoaded(sym: Symbol): Mirror = rootMirror
  val phases: Seq[Phase] = List(NoPhase, SomePhase)
  val phaseWithId: Array[Phase] = {
    val maxId = phases.map(_.id).max
    val phasesArray = Array.ofDim[Phase](maxId+1)
    phases foreach { phase =>
      phasesArray(phase.id) = phase
    }
    phasesArray
  }
  lazy val treeInfo: scala.reflect.internal.TreeInfo{val global: SymbolTableForUnitTesting.this.type} = ???

  val currentFreshNameCreator = new reflect.internal.util.FreshNameCreator

  phase = SomePhase

  type RuntimeClass = java.lang.Class[_]
  implicit val RuntimeClassTag: ClassTag[RuntimeClass] = ClassTag[RuntimeClass](classOf[RuntimeClass])
  implicit val MirrorTag: ClassTag[Mirror] = ClassTag[Mirror](classOf[GlobalMirror])
  implicit val TreeCopierTag: ClassTag[TreeCopier] = ClassTag[TreeCopier](classOf[TreeCopier])
}
