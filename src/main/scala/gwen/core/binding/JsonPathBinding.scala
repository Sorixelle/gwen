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

import scala.util.Try

object JsonPathBinding {
  
  def baseKey(name: String) = s"$name/${BindingType.`json path`}"
  private def pathKey(name: String) = s"${baseKey(name)}/expression"
  private def sourceKey(name: String) = s"${baseKey(name)}/source"

  def bind(name: String, jsonPath: String, source: String, env: EvalEnvironment): Unit = {
    env.scopes.set(pathKey(name), jsonPath)
    env.scopes.set(sourceKey(name), source)
  }

}

class JsonPathBinding[T <: EvalContext](name: String, ctx: T) extends Binding[T, String](name, ctx) {

  val pathKey = JsonPathBinding.pathKey(name)
  val sourceKey = JsonPathBinding.sourceKey(name)

  override def resolve(): String = {
    resolveValue(pathKey) { jsonPath => 
      resolveRef(sourceKey) { source =>
        ctx.evaluate(s"$$[dryRun:${BindingType.`json path`}]") {
          ctx.evaluateJsonPath(jsonPath, source)
        }
      }
    }
  }

  override def toString: String = Try {
    resolveValue(pathKey) { jsonPath => 
      resolveValue(sourceKey) { source =>
        s"$name [${BindingType.`json path`}: $jsonPath, source: $source]"
      }
    }
  } getOrElse name

}
