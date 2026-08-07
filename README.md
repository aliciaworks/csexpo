# csexpo — C# Export for Kotlin Multiplatform

Write a Kotlin Multiplatform library once, and call it from C# like it's just
another local library — no hand-written P/Invoke, no cinterop, no `.def` files.
The same "it just works" feeling Swift developers get from KMP's Swift Export,
but for C#.

## Why this exists

Kotlin/Native already makes Swift interop feel effortless: you compile your Kotlin
into a framework, and Swift code imports it and calls your API directly. Under the
hood the compiler builds a full model of your public API and turns it into a target
language module, so you never touch the C bridge.

`csexpo` does that for C#. A generator reads the compiled Kotlin klib (through the
Kotlin Analysis API — the exact same machinery JetBrains' Swift Export uses), builds
an intermediate model we call **CIR** (a "C# IR", mirroring Swift Export's SIR
design), and prints C# source files. The result is that your C# code looks like this:

```csharp
using com.example;

var user = new User("Tom", 30);
Console.WriteLine(TopLevelFunctions.TopLevelGreeting(user));
Console.WriteLine(new Greeter("Hi").Greet(user));
Console.WriteLine(Color.RED);
```

`User` comes through as a record, `Color` as an enum, `List<User>` as
`IReadOnlyList<User>`, and package-level functions land on a tidy static class.
All of the glue is generated — you just write the call.

To keep our behavior honest and aligned with the real thing, we keep a shallow,
sparse clone of the upstream `JetBrains/kotlin` source under `reference/` and lean on
it when mirroring the official Swift Export machinery.

## Layout

| Path | What's in there |
|---|---|
| `generator/` | The Kotlin/JVM CLI: klib → CIR → `.cs` files |
| `kmp-lib/` | A small sample KMP library that we export |
| `cs-consumer/` | A C# console app that consumes the generated bindings |
| `reference/` | Shallow+sparse clone of `JetBrains/kotlin` (the Swift Export reference) |

## Building and running

You'll need a JDK (17+, `JAVA_HOME` set), the .NET SDK, and internet access — the
first build pulls down Gradle and the Kotlin/Native toolchain.

```powershell
# 1. Compile the sample library into a klib
cd kmp-lib; .\gradlew.bat compileKotlinMingwX64; cd ..

# 2. Generate the C# bindings into cs-consumer/Generated
cd generator; .\gradlew.bat run --args="--klib ..\kmp-lib\build\classes\kotlin\mingwX64\main\klib\kmp-lib --output ..\cs-consumer\Generated"; cd ..

# 3. Build and run the C# consumer
dotnet run --project cs-consumer
```

Or skip the details and use the orchestration script:

```powershell
.\build.ps1 -Task all
```

## Where this project stands

- [x] Reference study — Swift Export's architecture, the SIR model, and the klib reader
- [x] Generator pipeline — klib → CIR → C# (classes, records, enums, collections, top-level functions)
- [ ] Native ABI bridge — make the generated calls actually execute against the Kotlin/Native binary
- [ ] Reverse direction — export C# to Kotlin via NativeAOT

So far the generated declarations are real and compile, but the method bodies are
still stubs. The next milestone wires them to the Kotlin/Native shared library
(which, on Windows, means installing the MSVC toolchain first).

## How the mapping lines up

| Swift Export concept | csexpo equivalent |
|---|---|
| compiled Kotlin/Native klib | same |
| SIR model (`org.jetbrains.kotlin.sir.*`) | CIR model (`csexpo.cir.*`) |
| `SirPrinter` → `.swift` | `CSharpPrinter` → `.g.cs` |
| Analysis API session | same, via `swift-export-embeddable` |
| native framework + swiftmodule | shared library + generated C# bridge |
