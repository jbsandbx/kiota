using System;
using System.IO;
using System.Linq;
using Xunit;

namespace Kiota.Builder.Writers.Python.Tests {
    public class CodePropertyWriterTests: IDisposable {
        private const string DefaultPath = "./";
        private const string DefaultName = "name";
        private readonly StringWriter tw;
        private readonly LanguageWriter writer;
        private readonly CodeProperty property;
        private readonly CodeClass parentClass;
        private const string PropertyName = "propertyName";
        private const string TypeName = "Somecustomtype";
        public CodePropertyWriterTests() {
            writer = LanguageWriter.GetLanguageWriter(GenerationLanguage.Python, DefaultPath, DefaultName);
            tw = new StringWriter();
            writer.SetTextWriter(tw);
            var root = CodeNamespace.InitRootNamespace();
            parentClass = new CodeClass {
                Name = "parentClass"
            };
            root.AddClass(parentClass);
            property = new CodeProperty {
                Name = PropertyName,
            };
            property.Type = new CodeType {
                Name = TypeName
            };
            parentClass.AddProperty(property, new() {
                Name = "pathParameters",
                Kind = CodePropertyKind.PathParameters,
            }, new() {
                Name = "requestAdapter",
                Kind = CodePropertyKind.RequestAdapter,
            });
        }
        public void Dispose() {
            tw?.Dispose();
            GC.SuppressFinalize(this);
        }
        [Fact]
        public void WritesRequestBuilder() {
            property.Kind = CodePropertyKind.RequestBuilder;
            writer.Write(property);
            var result = tw.ToString();
            Assert.Contains($"return {TypeName}(", result);
            Assert.Contains("self.request_adapter", result);
            Assert.Contains("self.path_parameters", result);
        }
        [Fact]
        public void WritesCustomProperty() {
            property.Kind = CodePropertyKind.Custom;
            writer.Write(property);
            var result = tw.ToString();
            Assert.Contains($": Optional[{TypeName}]", result);
        }
        [Fact]
        public void WritesPrivateSetter() {
            property.Kind = CodePropertyKind.Custom;
            property.ReadOnly = true;
            writer.Write(property);
            var result = tw.ToString();
            Assert.Contains("property_name", result);
        }
        [Fact]
        public void WritesFlagEnums() {
            property.Kind = CodePropertyKind.Custom;
            property.Type = new CodeType {
                Name = "customEnum",
            };
            (property.Type as CodeType).TypeDefinition = new CodeEnum {
                Name = "customEnumType",
                Flags = true,
            };
            writer.Write(property);
            var result = tw.ToString();
            Assert.Contains("CustomEnum", result);
        }
    }
}
