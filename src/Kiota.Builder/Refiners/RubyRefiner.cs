﻿using System;
using System.Collections.Generic;
using System.Linq;
using Kiota.Builder.Extensions;

namespace Kiota.Builder.Refiners {
    public class RubyRefiner : CommonLanguageRefiner, ILanguageRefiner
    {
        public RubyRefiner(GenerationConfiguration configuration) : base(configuration) {}
        public override void Refine(CodeNamespace generatedCode)
        {
            ReplaceIndexersByMethodsWithParameter(generatedCode, generatedCode, false, "_by_id");
            AddPropertiesAndMethodTypesImports(generatedCode, false, false, false);
            RemoveCancellationParameter(generatedCode);
            AddParsableImplementsForModelClasses(generatedCode, "MicrosoftKiotaAbstractions::Parsable");
            AddInheritedAndMethodTypesImports(generatedCode);
            AddDefaultImports(generatedCode, defaultUsingEvaluators);
            CorrectCoreType(generatedCode, null, CorrectPropertyType);
            AddGetterAndSetterMethods(generatedCode,
                new() {
                    CodePropertyKind.Custom,
                    CodePropertyKind.AdditionalData,
                    CodePropertyKind.BackingStore,
                },
                _configuration.UsesBackingStore,
                true,
                string.Empty,
                string.Empty);
            ReplaceReservedNames(generatedCode, new RubyReservedNamesProvider(), x => $"{x}_escaped");
            AddNamespaceModuleImports(generatedCode , _configuration.ClientNamespaceName);
            FixInheritedEntityType(generatedCode);
            var defaultConfiguration = new GenerationConfiguration();
            ReplaceDefaultSerializationModules(
                generatedCode,
                defaultConfiguration.Serializers,
                new (StringComparer.OrdinalIgnoreCase) {
                    "microsoft_kiota_serialization.JsonSerializationWriterFactory"});
            ReplaceDefaultDeserializationModules(
                generatedCode,
                defaultConfiguration.Deserializers,
                new (StringComparer.OrdinalIgnoreCase) {
                    "microsoft_kiota_serialization.JsonParseNodeFactory"});
            AddSerializationModulesImport(generatedCode,
                                        new [] { "microsoft_kiota_abstractions.ApiClientBuilder",
                                                "microsoft_kiota_abstractions.SerializationWriterFactoryRegistry" },
                                        new [] { "microsoft_kiota_abstractions.ParseNodeFactoryRegistry" });
        }
        private static void CorrectPropertyType(CodeProperty currentProperty) {
            if(currentProperty.IsOfKind(CodePropertyKind.PathParameters)) {
                currentProperty.Type.IsNullable = true;
                if(!string.IsNullOrEmpty(currentProperty.DefaultValue))
                    currentProperty.DefaultValue = "Hash.new";
            }
        }
        private static readonly AdditionalUsingEvaluator[] defaultUsingEvaluators = new AdditionalUsingEvaluator[] { 
            new (x => x is CodeProperty prop && prop.IsOfKind(CodePropertyKind.RequestAdapter),
                "microsoft_kiota_abstractions", "RequestAdapter"),
            new (x => x is CodeMethod method && method.IsOfKind(CodeMethodKind.RequestGenerator),
                "microsoft_kiota_abstractions", "HttpMethod", "RequestInformation"), //TODO add request options once ruby supports it
            new (x => x is CodeMethod method && method.IsOfKind(CodeMethodKind.RequestExecutor),
                "microsoft_kiota_abstractions", "ResponseHandler"),
            new (x => x is CodeMethod method && method.IsOfKind(CodeMethodKind.Serializer),
                "microsoft_kiota_abstractions", "SerializationWriter"),
            new (x => x is CodeMethod method && method.IsOfKind(CodeMethodKind.Deserializer),
                "microsoft_kiota_abstractions", "ParseNode"),
            new (x => x is CodeClass @class && @class.IsOfKind(CodeClassKind.Model),
                "microsoft_kiota_abstractions", "Parsable"),
            new (x => x is CodeMethod method && method.IsOfKind(CodeMethodKind.RequestExecutor),
                "microsoft_kiota_abstractions", "Parsable"),
            new (x => x is CodeMethod method && method.IsOfKind(CodeMethodKind.ClientConstructor) &&
                        method.Parameters.Any(y => y.IsOfKind(CodeParameterKind.BackingStore)),
                "microsoft_kiota_abstractions", "BackingStoreFactory", "BackingStoreFactorySingleton"),
            new (x => x is CodeProperty prop && prop.IsOfKind(CodePropertyKind.BackingStore),
                "microsoft_kiota_abstractions", "BackingStore", "BackedModel", "BackingStoreFactorySingleton" ),
        };
        protected static void AddInheritedAndMethodTypesImports(CodeElement currentElement) {
            if(currentElement is CodeClass currentClass && currentClass.IsOfKind(CodeClassKind.Model) 
                && currentClass.StartBlock.Inherits != null) {
                currentClass.AddUsing(new CodeUsing { Name = currentClass.StartBlock.Inherits.Name, Declaration = currentClass.StartBlock.Inherits});
            }
            CrawlTree(currentElement, (x) => AddInheritedAndMethodTypesImports(x));
        }

        protected static void FixInheritedEntityType(CodeElement currentElement, string prefix = ""){

            var nameSpaceName = string.IsNullOrEmpty(prefix) ? FetchEntityNamespace(currentElement).NormalizeNameSpaceName("::") : prefix; 
            if(currentElement is CodeClass currentClass && currentClass.IsOfKind(CodeClassKind.Model) 
                && currentClass.StartBlock.Inherits != null 
                && "entity".Equals(currentClass.StartBlock.Inherits.Name, StringComparison.OrdinalIgnoreCase)) {
                currentClass.StartBlock.Inherits.Name = prefix + currentClass.StartBlock.Inherits.Name.ToFirstCharacterUpperCase();
            }
            CrawlTree(currentElement, (c) => FixInheritedEntityType(c, nameSpaceName));
        }
        protected static string FetchEntityNamespace(CodeElement currentElement){
            Queue<CodeElement> children = new Queue<CodeElement>();
            children.Enqueue(currentElement);
            while(children.Count > 0){
                foreach(var childElement in children.Dequeue().GetChildElements())
                    if(childElement is CodeClass currentClass && currentClass.IsOfKind(CodeClassKind.Model) 
                    && "entity".Equals(currentClass?.Name, StringComparison.OrdinalIgnoreCase)) {
                        return string.IsNullOrEmpty(currentClass?.Parent?.Name) ? string.Empty : currentClass?.Parent?.Name + "::";
                    } else {
                        children.Enqueue(childElement);
                    }
            }
            return null;
        }
        protected void AddNamespaceModuleImports(CodeElement current, string clientNamespaceName) {
            const string dot = ".";
            if(current is CodeClass currentClass) {
                var Module = currentClass.GetImmediateParentOfType<CodeNamespace>();
                if(!string.IsNullOrEmpty(Module.Name)){
                    var modulesProperties = Module.Name.Replace(clientNamespaceName+dot, string.Empty).Split(dot);
                    for (int i = modulesProperties.Length - 1; i >= 0; i--){
                        var prefix = string.Concat(Enumerable.Repeat("../", modulesProperties.Length -i-1));
                        var usingName = modulesProperties[i].ToSnakeCase();
                        currentClass.AddUsing(new CodeUsing { 
                            Name = usingName,
                            Declaration = new CodeType {
                                IsExternal = true,
                                Name = $"{(string.IsNullOrEmpty(prefix) ? "./" : prefix)}{usingName}",
                            }
                        });
                    }
                }
            }
            CrawlTree(current, c => AddNamespaceModuleImports(c, clientNamespaceName));
        }
    }
}
