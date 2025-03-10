/*
 * Copyright 2024-2025 NetCracker Technology Corporation
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


	public ${targetFileName}(){
		// No action
	}

#foreach ($field in $object.fields)
#if ($field.comments.size() > 0)
	/**
#end	
#foreach ($comment in $field.comments)
	 * $comment
#end
#if ($field.comments.size() > 0)
	 */
#end
	${field.annotation}
	${field.javaTypeFullClassname} ${field.javaName};


#end

#foreach ($field in $object.fields)
##
####################################################################################################################################################
###########  Case 1 (standard case): the field's type is NOT a type that implements an interface defined in the GraphQL schema  ####################
####################################################################################################################################################
#if ($field.fieldJavaFullClassnamesFromImplementedInterface.size() == 0)
##
#if ($field.comments.size() > 0)
	/**
#end	
#foreach ($comment in $field.comments)
	 * $comment
#end
#if ($field.comments.size() > 0)
	 */
#end
	public void set${field.pascalCaseName}(${field.javaTypeFullClassname} ${field.javaName}) {
		this.${field.javaName} = ${field.javaName};
	}

#if ($field.comments.size() > 0)
	/**
#end	
#foreach ($comment in $field.comments)
	 * $comment
#end
#if ($field.comments.size() > 0)
	*/
#end
	public ${field.javaTypeFullClassname} get${field.pascalCaseName}() {
		return ${field.javaName};
	}
		
#else
####################################################################################################################################################
###########  Case 2: the field's type is implements an interface defined in the GraphQL schema  ####################################################
####################################################################################################################################################
##
#foreach ($type in $field.fieldJavaFullClassnamesFromImplementedInterface)
##
## The field inherited from an interface field, and the field's type has been narrowed (it's not the type defined in the interface, but a subclass or subinterface of it) 
## See IFoo and TFoo sample in the allGraphQLCases schema, used to check the #114 issue (search for 114 in allGraphQLCases.graphqls)

	/**
#foreach ($comment in $field.comments)
	 * $comment
#end
	 */
	@Override
	public void set${field.pascalCaseName}($type ${field.javaName}) {
#if ($field.javaType.startsWith("List<"))
		if (${field.javaName} == null || ${field.javaName} instanceof List) {
#if ($field.javaTypeFullClassname != $type)
			// ${field.javaName} is an instance of $type. Let's check that this can be copied into a ${field.javaType} 
			for (Object item : ${field.javaName}) {
				if (! (item instanceof ${field.type.classFullName}))
					throw new IllegalArgumentException("The given ${field.javaName} should be a list of instances of ${field.type.classFullName}, but at least one item is an instance of "
							+ item.getClass().getName());
			}
#end
			this.${field.javaName} = (${field.javaTypeFullClassname}) (Object) ${field.javaName};
#else
		if (${field.javaName} == null || ${field.javaName} instanceof ${field.javaTypeFullClassname}) {
			this.${field.javaName} = (${field.javaTypeFullClassname}) ${field.javaName};
#end
		} else {
			throw new IllegalArgumentException("The given ${field.javaName} should be an instance of ${field.javaTypeFullClassname}, but is an instance of "
					+ ${field.javaName}.getClass().getName());
		}
	}
#end ##(foreach ($type in $field.fieldJavaFullClassnamesFromImplementedInterface))

#if (!$field.fieldJavaFullClassnamesFromImplementedInterface.contains($field.javaTypeFullClassname))

	/** 
	 * As the type declared in the class is not inherited from one of the implemented interfaces, we need a dedicated setter.
#if ($field.javaType.startsWith("List<"))
	 * <br/>
	 * As the GraphQL type of this field is a list of items that are not of the same type as the field defined in the implemented interface, 
	 * we need to have a dedicated setter with a specific name. This is due to Java that does type erasure on parameterized types (for 
	 * compatibility reasons with older java versions). As Java can't detect at runtime the type of the items of the list, it can't 
	 * decide which setter to call. To overcome this issue, this setter has a dedicated name.
	 * 
	 * @param
#foreach ($comment in $field.comments)
	 * $comment
#end
	 */
	public void set${field.pascalCaseName}${field.graphQLTypeSimpleName}(${field.javaTypeFullClassname} ${field.javaName}) {
#else
	 * 
	 * @param
#foreach ($comment in $field.comments)
	 * $comment
#end
	 */
	public void set${field.pascalCaseName}(${field.javaTypeFullClassname} ${field.javaName}) {
#end
		this.${field.javaName} = ${field.javaName};
	}
#end

#if ($field.fieldJavaFullClassnamesFromImplementedInterface.size()>1 && $field.javaType.startsWith("List<"))
##
## We are in the complex case: the type is a list. And because of java's type erasure, we need to have different methods, for each possible return type.
## So we need to have more than one getter for this field. And these getters must have different name.
## Please note that this works for one level inheritance, for instance like in the allGraphQLCases test case, with TList implementing IList.
## For multiple levels like the one below, there is no java solution:
## 
## interface IFoo1 { 
##   ...
## }
## 
## interface IFoo2 implements IFoo1 { 
##   ...
## }
## 
## type TFoo2 implements IFoo2 { 
##   ...
## }
## 
## interface IList1 {
##   list: [IFoo1]
## }
## 
## interface IList2 implements IList1 {
##   list: [IFoo2]
## }
## 
## type TList implements IList {
## 	list: [TFoo2]
## }
## 
## For this to work, the java interface IList2 must have these two methods (which is not possible) :
## List<IFoo2> getList();
## List<IFoo1> getList();
##
## So, in this case (!$field.fieldJavaFullClassnamesFromImplementedInterface.size()>1 && $field.javaType.startsWith("List<")), we throw an exception:
##
${exceptionThrower.throwRuntimeException("For fields which type are a list, the GraphQL type may not be a GraphQl type that implements an interface that itself implements an interface. Only one level of inheritance is accepted, due to java syntax limitation. Sample: TList implements IList2 that itself implements IList1. TList contains an attribute 'list' that is a list of TFoo, where TFoo implements IFoo1 that itself implements IFoo2. It comes from IList2.list (list of IFoo2), which itself comes from IList1 (list of IFoo1). In this case, TList must implement these two methods: 'List<IFoo2> getList()' and ' List<IFoo1> getList()', which is not possible.")}
#end
##
##
##
## There is only one item in the fieldJavaFullClassnamesFromImplementedInterface.
## If this field is not a list, only the normal getter is enough. It will override the getters from the implemented interface(s)
## But if this field is a list, and the field's type is not the same as in the implemented interface (for instance [TFoo2] versus [IFoo2] as in the above sample), then
## we need separate getters. In this case, we need these getters:
##
## List<IFoo> getList();  // This one overrides the getter from the implemented interface.
## List<TFoo> getListTFoo();  // This one returns the list with the good type, as defined for the current field, of the current object we're generated the code for.
##
##
#if ($field.javaType.startsWith("List<"))
#foreach ($supertype in $field.fieldJavaFullClassnamesFromImplementedInterface) ##This is a Set with one item. As it is a Set, we can not do a get(0), so we iterate over it.
	/**
#foreach ($comment in $field.comments)
	 * $comment
#end
	 */
	@Override
	@SuppressWarnings("unchecked")
	public $supertype get${field.pascalCaseName}() {
		return ($supertype) (Object) ${field.javaName};
	}

#end
#end
	/**
#foreach ($comment in $field.comments)
	 * $comment
#end
	 */
#if (!$field.javaType.startsWith("List<"))
	@Override
#end
	public ${field.javaTypeFullClassname} get${field.pascalCaseName}#if ($field.javaType.startsWith("List<"))${field.graphQLTypeSimpleName}#end() {
		return ${field.javaName};
	}
#end

#end ##end of test "if ($field.fieldJavaFullClassnamesFromImplementedInterface.size() == 0)"
####################################################################################################################################################
####################################################################################################################################################

    public String toString() {
        return "${object.javaName} {"
#foreach ($field in $object.fields)
				+ "${field.javaName}: " + ${field.javaName}
#if($foreach.hasNext)
				+ ", "
#end 
#end
        		+ "}";
    }

## Issue 130: if the GraphQL type's name is Builder, the inner static class may not be named Builder. So we prefix it with '_'
	public static#if($targetFileName=="Builder") _Builder#else Builder#end builder() {
		return new#if($targetFileName=="Builder") _Builder#else Builder#end();
	}

	/**
	 * The Builder that helps building instance of this POJO. You can get an instance of this class, by calling the
	 * {@link #builder()}
#if($targetFileName=="Builder")
	 * <br/>As this GraphQL type's name is Builder, the inner Builder class is renamed to _Builder, to avoid name 
	 * collision during Java compilation.
#end 
	 */
	public static class#if($targetFileName=="Builder") _Builder#else Builder#end {
#foreach ($field in $object.fields)
#if(${field.javaName} != '__typename')
		private ${field.javaTypeFullClassname} ${field.javaName};
#end
#end

#foreach ($field in $object.fields)
#if(${field.javaName} != '__typename')
		public#if($targetFileName=="Builder") _Builder#else Builder#end with${field.pascalCaseName}(${field.javaTypeFullClassname} ${field.javaName}) {
			this.${field.javaName} = ${field.javaName};
			return this;
		}
#end
#end

		public ${targetFileName} build() {
			${targetFileName} _object = new ${targetFileName}();
#foreach ($field in $object.fields)
#if(${field.javaName} == '__typename')
			_object.set__typename("${object.javaName}");
#else
#if ($field.fieldJavaFullClassnamesFromImplementedInterface.size()>0 && !$field.fieldJavaFullClassnamesFromImplementedInterface.contains($field.javaTypeFullClassname) && $field.javaType.startsWith("List<"))
			_object.set${field.pascalCaseName}${field.graphQLTypeSimpleName}(${field.javaName});
#else
			_object.set${field.pascalCaseName}(${field.javaName});
#end
#end
#end
			return _object;
		}
	}
