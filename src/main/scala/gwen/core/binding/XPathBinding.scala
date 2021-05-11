/*
 * Copyright 2021 Branko Juric, Brady Wood
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gwen.core.engine.binding

import gwen.core.engine.EvalContext
import gwen.core.engine.EvalEnvironment
import gwen.core.engine.support.XMLNodeType

import scala.util.Try

object XPathBinding {

  def baseKey(name: String) = s"$name/${BindingType.xpath}"
  private def xpathKey(name: String) = s"${baseKey(name)}/expression"
  private def targetKey(name: String) = s"${baseKey(name)}/targetType"
  private def sourceKey(name: String) = s"${baseKey(name)}/source"

  def bind(name: String, xpath: String, target: String, source: String, env: EvalEnvironment): Unit = {
    env.scopes.set(xpathKey(name), xpath)
    env.scopes.set(targetKey(name), target)
    env.scopes.set(sourceKey(name), source)
  }

}

class XPathBinding[T <: EvalContext](name: String, ctx: T) extends Binding[T, String](name, ctx) {

  val xpathKey = XPathBinding.xpathKey(name)
  val targetKey = XPathBinding.targetKey(name)
  val sourceKey = XPathBinding.sourceKey(name)

  override def resolve(): String = {
    resolveValue(xpathKey) { xpath =>
      resolveValue(targetKey) { target =>
        resolveRef(sourceKey) { source =>
          ctx.evaluate(s"$$[dryRun:${BindingType.xpath}]") {
            ctx.evaluateXPath(xpath, source, XMLNodeType.withName(target))
          }
        }
      }
    }
  }

  override def toString: String = Try {
    resolveValue(xpathKey) { xpath =>
      resolveValue(targetKey) { target =>
        resolveValue(sourceKey) { source =>
          s"$name [${BindingType.xpath}: $xpath, target: $target, source: $source]"
        }
      }
    }
  } getOrElse name

}
