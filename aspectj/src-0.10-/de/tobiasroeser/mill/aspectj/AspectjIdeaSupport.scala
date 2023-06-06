package de.tobiasroeser.mill.aspectj

import mill.T
import mill.define.Command
import mill.scalalib.GenIdeaImpl
import mill.scalalib.GenIdeaModule.{Element, IdeaConfigFile, JavaFacet}

trait AspectjIdeaSupport extends AspectjModule {
  // Experimental support for Aspectj compiler config
  override def ideaConfigFiles(ideaConfigVersion: Int): Command[Seq[IdeaConfigFile]] =
    T.command {
      ideaConfigVersion match {
        case 4 =>
          aspectjToolsClasspath().toIndexedSeq match {
            case IndexedSeq() =>
              Seq()
            case toolsPath =>
              Seq(
                IdeaConfigFile(
                  name = "compiler.xml",
                  component = "AjcSettings",
                  config = Seq(Element(
                    "option",
                    Map("name" -> "ajcPath", "value" -> toolsPath.head.path.toIO.getPath())
                  ))
                ),
                IdeaConfigFile(
                  name = "compiler.xml",
                  component = "CompilerConfiguration",
                  config =
                    Seq(Element("option", Map("name" -> "DEFAULT_COMPILER", "value" -> "ajc")))
                )
              )
          }
        case v =>
          T.log.error(s"Unsupported Idea config version ${v}")
          Seq()

      }
    }

  // experimental support for AspectJ facets
  override def ideaJavaModuleFacets(ideaConfigVersion: Int): Command[Seq[JavaFacet]] =
    T.command {
      ideaConfigVersion match {
        case 4 =>
          val aspectPath =
            resolvedAspectIvyDeps().toSeq.map { depPathRef =>
              Element(
                "projectLibrary",
                childs = Seq(
                  Element("option", Map("name" -> "name", "value" -> depPathRef.path.last))
                )
              )
            } ++ aspectModuleDeps.map { module =>
              Element(
                "module",
                childs = Seq(
                  Element(
                    "option",
                    Map(
                      "name" -> "name",
                      "value" -> GenIdeaImpl.moduleName(module.millModuleSegments)
                    )
                  )
                )
              )
            }

          Seq(
            JavaFacet(
              "AspectJ",
              "AspectJ",
              config =
                Element(
                  "configuration",
                  childs =
                    if (aspectPath.isEmpty) Seq()
                    else Seq(
                      Element(
                        "option",
                        attributes = Map("name" -> "aspectPath"),
                        childs = aspectPath
                      )
                    )
                )
            )
          )
        case v =>
          T.log.error(s"Unsupported Idea config version ${v}")
          Seq()
      }
    }

}
