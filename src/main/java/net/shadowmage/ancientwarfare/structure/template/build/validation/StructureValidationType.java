package net.shadowmage.ancientwarfare.structure.template.build.validation;

import net.shadowmage.ancientwarfare.structure.template.build.validation.properties.IStructureValidationProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import static net.shadowmage.ancientwarfare.structure.template.build.validation.properties.StructureValidationProperties.*;

public enum StructureValidationType {

	GROUND(StructureValidatorGround::new),
	UNDERGROUND(StructureValidatorUnderground::new, MIN_GENERATION_DEPTH, MAX_GENERATION_DEPTH, MIN_OVERFILL),
	SKY(StructureValidatorSky::new, MIN_GENERATION_HEIGHT, MAX_GENERATION_HEIGHT, MIN_FLYING_HEIGHT),
	WATER(StructureValidatorWater::new),
	UNDERWATER(StructureValidatorUnderwater::new, MIN_WATER_DEPTH, MAX_WATER_DEPTH),
	HARBOR(StructureValidatorHarbor::new),
	ISLAND(StructureValidatorIsland::new, MIN_WATER_DEPTH, MAX_WATER_DEPTH);

	private final Supplier<? extends StructureValidator> createValidator;

	private List<IStructureValidationProperty> properties = new ArrayList<>();

	StructureValidationType(Supplier<? extends StructureValidator> createValidator, IStructureValidationProperty... props) {
		this.createValidator = createValidator;

		Collections.addAll(properties,
				SURVIVAL, WORLD_GEN, UNIQUE, PRESERVE_BLOCKS,
				SELECTION_WEIGHT, CLUSTER_VALUE, MIN_DUPLICATE_DISTANCE,
				BORDER_SIZE, MAX_LEVELING, MAX_FILL,
				BIOME_WHITE_LIST, DIMENSION_WHITE_LIST, BIOME_LIST, BLOCK_LIST, DIMENSION_LIST,
				BLOCK_SWAP);
		Collections.addAll(properties, props);
	}

	public List<IStructureValidationProperty> getValidationProperties() {
		return this.properties;
	}

	public String getName() {
		return name().toLowerCase(Locale.ENGLISH);
	}

	public StructureValidator getValidator() {
		return createValidator.get();
	}

	public static Optional<StructureValidationType> getTypeFromName(String name) {
		try {
			return Optional.of(StructureValidationType.valueOf(name.toUpperCase(Locale.ENGLISH)));
		}
		catch (IllegalArgumentException illegal) {
			return Optional.empty();
		}
	}
}
