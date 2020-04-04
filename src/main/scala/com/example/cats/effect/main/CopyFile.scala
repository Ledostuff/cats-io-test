package com.example.cats.effect.main

import java.io.File

import cats.effect.{ExitCode, IO, IOApp}
import com.example.cats.effect.interpreter.IoEffectCancelFileCopier

object CopyFile extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    for {
      _ <- if (args.length < 2) IO.raiseError(new IllegalArgumentException("Need origin and destination files."))
            else IO.unit
      in = new File(args(0))
      out = new File(args(1))
      count <- (new IoEffectCancelFileCopier).copy[IO](in, out)
      _ <- IO(println(s"$count bytes copied from ${in.getPath} to ${out.getPath}"))
    } yield ExitCode.Success
  }
}
