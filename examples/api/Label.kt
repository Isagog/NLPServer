/* Copyright 2020-present Simone Cangialosi. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package api

import com.kotlinnlp.ApiClient
import com.kotlinnlp.api.NlpServiceApi
import com.kotlinnlp.api.model.InputText
import com.kotlinnlp.api.model.Labeling
import com.xenomachina.argparser.mainBody

/**
 * Test the `label` method of the KotlinNLP APIs.
 *
 * Launch with the '-h' option for help about the command line arguments.
 */
fun main(args: Array<String>) = mainBody {

  val parsedArgs = CommandLineArguments(args)
  val client = NlpServiceApi(ApiClient().setBasePath("http://${parsedArgs.host}:${parsedArgs.port}"))

  inputLoop { text ->

    val labelings: List<Labeling> = client.label(InputText().text(text), false)

    println()

    labelings.forEach {

      println("Domain: ${it.domain}")

      it.sentences.forEachIndexed { i, sentence ->

        println("\tSentence #${i + 1}:")

        sentence.tokens.forEach { tk ->
          val tag = if (tk.iob == "O") "  ${tk.iob}  " else "${tk.iob}-${tk.label}"
          println("\t\t%3.0f%% - $tag | ${tk.form}".format(100.0 * tk.score))
        }
      }
    }
  }
}
