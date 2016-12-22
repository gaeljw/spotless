/*
 * Copyright 2016 DiffPlug
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
package com.diffplug.spotless;

import java.io.File;
import java.io.Serializable;
import java.util.Objects;
import java.util.Random;

import com.diffplug.common.debug.LapTimer;
import com.diffplug.common.debug.StepProfiler;
import com.diffplug.spotless.FormatterStep.Strict;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Standard implementation of FormatExtension which cleanly enforces
 * separation of serializable configuration and a pure format function.
 *
 * Not an inner-class of FormatterStep so that it can stay entirely private
 * from the API.
 */
@SuppressFBWarnings("SE_TRANSIENT_FIELD_NOT_RESTORED")
public abstract class FormatterStepImpl<Key extends Serializable> extends Strict<Key> {
	public static final StepProfiler PROFILER = new StepProfiler(LapTimer.createNanoWrap2Sec());

	private static final long serialVersionUID = 1L;

	/** Transient because only the key matters. */
	final transient String name;

	/** Transient because only the key matters. */
	final transient ThrowingEx.Supplier<Key> keySupplier;

	FormatterStepImpl(String name, ThrowingEx.Supplier<Key> keySupplier) {
		this.name = Objects.requireNonNull(name);
		this.keySupplier = Objects.requireNonNull(keySupplier);
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	protected Key calculateKey() throws Exception {
		try {
			PROFILER.startStep(name + " calculateKey");
			return keySupplier.get();
		} finally {
			PROFILER.finish();
		}
	}

	static final class Standard<Key extends Serializable> extends FormatterStepImpl<Key> {
		private static final long serialVersionUID = 1L;

		final transient ThrowingEx.Function<Key, FormatterFunc> keyToFormatter;
		transient FormatterFunc formatter; // initialized lazily

		Standard(String name, ThrowingEx.Supplier<Key> keySupplier, ThrowingEx.Function<Key, FormatterFunc> keyToFormatter) {
			super(name, keySupplier);
			this.keyToFormatter = Objects.requireNonNull(keyToFormatter);
		}

		@Override
		protected String format(Key key, String rawUnix, File file) throws Exception {
			if (formatter == null) {
				PROFILER.startStep(name + " createFormatter");
				formatter = keyToFormatter.apply(key());
				PROFILER.finish();
			}
			try {
				PROFILER.startStep(name + " apply");
				return formatter.apply(rawUnix);
			} finally {
				PROFILER.finish();
			}
		}
	}

	static class Closeable<Key extends Serializable> extends FormatterStepImpl<Key> {
		private static final long serialVersionUID = 1L;

		final transient ThrowingEx.Function<Key, FormatterFunc.Closeable> keyToFormatter;
		transient FormatterFunc.Closeable formatter; // initialized lazily

		Closeable(String name, ThrowingEx.Supplier<Key> keySupplier, ThrowingEx.Function<Key, FormatterFunc.Closeable> keyToFormatter) {
			super(name, keySupplier);
			this.keyToFormatter = Objects.requireNonNull(keyToFormatter);
		}

		@Override
		protected String format(Key key, String rawUnix, File file) throws Exception {
			if (formatter == null) {
				PROFILER.startStep(name + " openFormatter");
				formatter = keyToFormatter.apply(key());
				PROFILER.finish();
			}
			try {
				PROFILER.startStep(name + " apply");
				return formatter.apply(rawUnix);
			} finally {
				PROFILER.finish();
			}
		}

		@Override
		public void finish() {
			if (formatter != null) {
				PROFILER.startStep(name + " closeFormatter");
				formatter.close();
				PROFILER.finish();
				formatter = null;
			}
		}
	}

	/** Formatter which is equal to itself, but not to any other Formatter. */
	static class NeverUpToDate extends FormatterStepImpl<Integer> {
		private static final long serialVersionUID = 1L;

		private static final Random RANDOM = new Random();

		final transient ThrowingEx.Supplier<FormatterFunc> formatterSupplier;
		transient FormatterFunc formatter; // initialized lazily

		NeverUpToDate(String name, ThrowingEx.Supplier<FormatterFunc> formatterSupplier) {
			super(name, RANDOM::nextInt);
			this.formatterSupplier = formatterSupplier;
		}

		@Override
		protected String format(Integer key, String rawUnix, File file) throws Exception {
			if (formatter == null) {
				formatter = formatterSupplier.get();
			}
			return formatter.apply(rawUnix);
		}
	}
}
