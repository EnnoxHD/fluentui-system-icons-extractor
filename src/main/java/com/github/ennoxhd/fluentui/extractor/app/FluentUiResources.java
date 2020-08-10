package com.github.ennoxhd.fluentui.extractor.app;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class FluentUiResources {

	public static void main(String args[]) {

		// Defaults

		final int DEFAULT_SIZE = 24;
		final Set<String> DEFAULT_TYPES = Collections.unmodifiableSet(Set.of("regular", "filled"));
		final boolean USE_ORIGINAL_NAME = false;

		// Data

		final Path resources = Path.of(System.getProperty("user.home") + "/Downloads/fluentui-system-icons-1.1.43/assets");
		final Path output = Path.of(System.getProperty("user.home") + "/Downloads/fluentui");

		// Read data

		System.out.println("Reading data...");

		final Map<IconTypeSize, File> allIcons = readData(resources);
		if(allIcons == null) exit();

		final Set<String> icons = allIcons.keySet().stream()
				.map(key -> key.icon).distinct()
				.collect(Collectors.toUnmodifiableSet());
		final Set<Integer> sizes = allIcons.keySet().stream()
				.map(key -> key.size).distinct()
				.collect(Collectors.toUnmodifiableSet());
		final Set<String> types = allIcons.keySet().stream()
				.map(key -> key.type).distinct()
				.collect(Collectors.toUnmodifiableSet());

		// Analyze

		System.out.println("Analyzing...");

		final int resourceIconCount = resources.toFile().listFiles().length;
		if(resourceIconCount != icons.size()) {
			System.out.println("Did not found all resources! (" + icons.size() + " of " + resourceIconCount + " icons)");
			exit();
		} else {
			System.out.println("Found all icon resources. (" + icons.size() + ")");
		}

		final Set<IconTypeSize> defaultSized = allIcons.keySet().stream()
				.filter(key -> key.size == DEFAULT_SIZE)
				.filter(distinctByKey(key -> key.icon))
				.collect(Collectors.toUnmodifiableSet());
		final Set<String> defaultSizedIcons = defaultSized.stream()
				.map(keyInner -> keyInner.icon)
				.collect(Collectors.toUnmodifiableSet());
		if(!sizes.contains(DEFAULT_SIZE)) {
			System.out.println("No icon found with default size!");
			exit();
		} else {
			System.out.println(defaultSized.size() + " icons of " + icons.size() + " are default sized.");
		}

		final Set<String> unknownDefaultTypes = DEFAULT_TYPES.stream()
				.filter(defaultType -> !types.contains(defaultType))
				.collect(Collectors.toUnmodifiableSet());
		unknownDefaultTypes.stream()
				.forEach(type -> System.out.println("The default type " + type + "is unknown."));
		if(unknownDefaultTypes.size() > 0) {
			exit();
		} else {
			System.out.println("All default types are known.");
		}

		final Set<String> additionalTypes = types.stream()
				.filter(type -> !DEFAULT_TYPES.contains(type))
				.collect(Collectors.toUnmodifiableSet());
		if(additionalTypes.size() > 0) {
			System.out.println("There are " + additionalTypes.size() + " addtional types available:");
			additionalTypes.stream().forEach(System.out::println);
		} else {
			System.out.println("No additional types found.");
		}

		// Decide

		System.out.println("Curating icons...");

		final Set<IconTypeSize> notDefaultSized = allIcons.keySet().stream()
				.filter(key -> !defaultSizedIcons.contains(key.icon))
				.collect(Collectors.toUnmodifiableSet());

		final Set<IconTypeSize> notDefaultSizedMax = notDefaultSized.stream()
				.filter(key -> key.size.equals(notDefaultSized.stream()
						.filter(k -> k.icon.equals(key.icon) && k.type.equals(key.type))
						.map(k -> k.size)
						.max(Integer::compareTo)
						.get())
				)
				.collect(Collectors.toUnmodifiableSet());

		final Set<IconTypeSize> curatedIcons = allIcons.keySet().stream()
				.filter(key -> DEFAULT_TYPES.contains(key.type))
				.filter(key -> key.size == DEFAULT_SIZE || notDefaultSizedMax.contains(key))
				.collect(Collectors.toUnmodifiableSet());

		System.out.println("Curated " + curatedIcons.size() + " icons.");

		DEFAULT_TYPES.stream()
				.forEach(type ->
					System.out.println(curatedIcons.stream()
							.filter(key -> key.type.equals(type))
							.count()
						+ " icons are of type " + type + ".")
				);

		// Copy resources

		System.out.println("Copying resources...");

		final File outputFile = output.toFile();
		final boolean createdNewDir = outputFile.mkdirs();
		if(!outputFile.isDirectory()) {
			System.out.println("Could not create output directory '" + outputFile + "'!");
			exit();
		} else if(createdNewDir) {
			System.out.println("Created output directory '" + outputFile + "'.");
		} else {
			System.out.println("Using existing output directory '" + outputFile + "'.");
		}
		final Map<String, File> outputDirs = DEFAULT_TYPES.stream()
				.map(type -> new AbstractMap.SimpleImmutableEntry<String, File>(type, new File(outputFile, type)))
				.collect(Collectors.toUnmodifiableMap(AbstractMap.SimpleImmutableEntry::getKey,
						AbstractMap.SimpleImmutableEntry::getValue));
		outputDirs.values().stream()
				.forEach(dir -> {
					final boolean newDir = dir.mkdirs();
					if(!dir.isDirectory()) {
						System.out.println("Could not create output directory '" + dir + "'!");
						exit();
					} else if(newDir) {
						System.out.println("Created output directory '" + dir + "'.");
					} else {
						System.out.println("Using existing output directory '" + dir + "'.");
					}
				});

		curatedIcons.stream()
				.forEach(key -> {
					Path source = allIcons.get(key).toPath();
					String name = USE_ORIGINAL_NAME ? source.getFileName().toString() : key.icon.replace('_', '-') + ".svg";
					Path target = new File(outputDirs.get(key.type), name).toPath();
					try {
						Files.copy(source, target);
					} catch (IOException e) {
						e.printStackTrace();
						exit();
					}
				});
		
		outputDirs.values().stream()
				.forEach(sourceDir -> {
					outputDirs.values().stream()
							.filter(targetDir -> !targetDir.equals(sourceDir))
							.forEach(targetDir -> {
								Set<String> targetFileNames = Set.of(targetDir.listFiles()).stream()
										.map(file -> file.getName())
										.collect(Collectors.toUnmodifiableSet());
								Set.of(sourceDir.listFiles()).stream()
										.filter(sourceFile -> !targetFileNames.contains(sourceFile.getName()))
										.forEach(sourceFile -> {
											try {
												Files.copy(sourceFile.toPath(), new File(targetDir, sourceFile.getName()).toPath());
											} catch (IOException e) {
												e.printStackTrace();
												exit();
											}
										});
							});
				});

		System.out.println("Done!");
	}

	public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
		Map<Object, Boolean> seen = new ConcurrentHashMap<>();
		return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
	}

	private static final void exit() {
		System.out.println("exiting...");
		System.exit(0);
	}

	private static final Map<IconTypeSize, File> readData(final Path resources) {
		final Pattern filenamePattern = Pattern.compile("^ic_fluent_(?<name>[0-9a-z_]+)_(?<size>[0-9]+)_(?<type>[a-z]+)\\.svg$");
		try {
			return Files.walk(resources, 3)
					.map(path -> path.toFile())
					.filter(file -> file.isFile() && file.getName().endsWith(".svg"))
					.map(file -> {
						final Matcher matcher = filenamePattern.matcher(file.getName());
						if(!matcher.find()) {
							System.out.println("No match for: " + file.getName());
							exit();
						}
						final String icon = matcher.group("name");
						final Integer size = Integer.valueOf(matcher.group("size"));
						final String type = matcher.group("type");
						return new AbstractMap.SimpleImmutableEntry<>(new IconTypeSize(icon, type, size), file);
					})
					.collect(
						Collectors.toUnmodifiableMap(AbstractMap.SimpleImmutableEntry::getKey,
								AbstractMap.SimpleImmutableEntry::getValue)
					);
		} catch (IOException e) {
			System.out.println("Could not read data.");
			e.printStackTrace();
			exit();
		}
		return null;
	}

	private static final class IconTypeSize implements Comparable<IconTypeSize> {
		private String icon;
		private String type;
		private Integer size;

		public IconTypeSize(final String icon, final String type, final Integer size) {
			this.icon = icon;
			this.type = type;
			this.size = size;
		}

		@Override
		public final boolean equals(final Object o) {
			if(this == o) return true;
			if(!(o instanceof IconTypeSize)) return false;
			IconTypeSize other = (IconTypeSize) o;
			return Objects.equals(icon, other.icon)
					&& Objects.equals(type, other.type)
					&& Objects.equals(size, other.size);
		}

		@Override
		public final int hashCode() {
			return Objects.hash(icon, type, size);
		}

		@Override
		public final String toString() {
			return "[Icon: " + icon + ", Type: " + type + ", Size: " + size + "]";
		}

		@Override
		public final int compareTo(final IconTypeSize o) {
			final int icon = this.icon.compareTo(o.icon);
			if(icon != 0) return icon;
			final int type = this.type.compareTo(o.type);
			if(type != 0) return type;
			return this.size.compareTo(o.size);
		}
	}
}
