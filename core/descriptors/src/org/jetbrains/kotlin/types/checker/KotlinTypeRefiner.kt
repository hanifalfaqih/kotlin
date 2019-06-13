/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.types.checker

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeConstructor
import org.jetbrains.kotlin.types.UnwrappedType
import org.jetbrains.kotlin.types.refinement.TypeRefinement
import org.jetbrains.kotlin.types.refinement.TypeRefinementInternal

abstract class KotlinTypeRefiner {
    abstract val moduleDescriptor: ModuleDescriptor

    @TypeRefinement
    abstract fun refineType(type: KotlinType): KotlinType

    @TypeRefinement
    abstract fun refineSupertypes(classDescriptor: ClassDescriptor): Collection<KotlinType>

    @TypeRefinement
    abstract fun refineDescriptor(descriptor: DeclarationDescriptor): ClassifierDescriptor?

    @TypeRefinement
    abstract fun refineTypeAliasTypeConstructor(typeAliasDescriptor: TypeAliasDescriptor): TypeConstructor?

    @TypeRefinement
    abstract fun findClassAcrossModuleDependencies(classId: ClassId): ClassDescriptor?

    @TypeRefinement
    abstract fun isRefinementNeededForModule(moduleDescriptor: ModuleDescriptor): Boolean

    @TypeRefinement
    abstract fun isRefinementNeededForTypeConstructor(typeConstructor: TypeConstructor): Boolean

    @TypeRefinement
    abstract fun <S : MemberScope> getOrPutScopeForClass(classDescriptor: ClassDescriptor, compute: () -> S): S

    object Default : KotlinTypeRefiner() {
        override val moduleDescriptor: ModuleDescriptor
            get() = TODO()

        @TypeRefinement
        override fun refineType(type: KotlinType): KotlinType = type

        @TypeRefinement
        override fun refineSupertypes(classDescriptor: ClassDescriptor): Collection<KotlinType> {
            return classDescriptor.typeConstructor.supertypes
        }

        @TypeRefinement
        override fun refineDescriptor(descriptor: DeclarationDescriptor): ClassDescriptor? {
            return null
        }

        @TypeRefinement
        override fun refineTypeAliasTypeConstructor(typeAliasDescriptor: TypeAliasDescriptor): TypeConstructor? {
            return null
        }

        @TypeRefinement
        override fun findClassAcrossModuleDependencies(classId: ClassId): ClassDescriptor? {
            return null
        }

        @TypeRefinement
        override fun isRefinementNeededForModule(moduleDescriptor: ModuleDescriptor): Boolean {
            return false
        }

        @TypeRefinement
        override fun isRefinementNeededForTypeConstructor(typeConstructor: TypeConstructor): Boolean {
            return false
        }

        @TypeRefinement
        override fun <S : MemberScope> getOrPutScopeForClass(classDescriptor: ClassDescriptor, compute: () -> S): S {
            return compute()
        }
    }
}

@TypeRefinement
fun KotlinTypeRefiner.refineTypes(types: Iterable<KotlinType>): List<KotlinType> = types.map { refineType(it) }

@TypeRefinement
@JvmName("refineUnwrappedTypes")
fun KotlinTypeRefiner.refineTypes(types: Iterable<UnwrappedType>): List<UnwrappedType> = types.map { refineType(it).unwrap() }

class Ref<T : Any>(var value: T?)

@TypeRefinementInternal
val REFINER_CAPABILITY = ModuleDescriptor.Capability<Ref<KotlinTypeRefiner>>("KotlinTypeRefiner")